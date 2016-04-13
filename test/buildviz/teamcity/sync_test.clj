(ns buildviz.teamcity.sync-test
  (:require [buildviz.teamcity.sync :as sut]
            [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.fake :as fake]
            [clojure.test :refer :all]))

(defn- successful-json-response [body]
  (fn [_] {:status 200
           :body (j/generate-string body)}))

(defn- a-job [id project-name job-name]
  {:id id
   :projectName project-name
   :name job-name})

(defn- a-project [id & jobs]
  [["http://teamcity:8000/httpAuth/app/rest/projects/the_project"
    (successful-json-response {:buildTypes {:buildType jobs}})]])

(defn- a-job-with-builds [job-id & builds]
  (let [job-builds [(format "http://teamcity:8000/httpAuth/app/rest/buildTypes/id:%s/builds/?fields=build(id,number,status,startDate,finishDate,revisions(revision(version,vcs-root-instance)))"
                            job-id)
                    (successful-json-response {:build (map #(merge {:revisions []
                                                                    :status "SUCCESS"
                                                                    :state "finished"}
                                                                   %) builds)})]
        testresults (map (fn [build]
                           [(format "http://teamcity:8000/httpAuth/app/rest/testOccurrences?locator=count:100,start:0,build:(id:%s)"
                                    (:id build))
                            (successful-json-response {:testOccurrences []})]) builds)]
    (cons job-builds testresults)))

(defn- capture-puts-to-buildviz-in [map-ref]
  [[#"http://buildviz:8010/builds/(.+)/(.+)"
    (fn [req]
      (swap! map-ref #(conj % [(:uri req)
                               (j/parse-string (slurp (:body req)) true)]))
      {:status 200 :body ""})]])

(defn- serve-up [& routes]
  (->> routes
       (mapcat identity) ; flatten once
       (into {})))

(deftest test-main
  (testing "should sync a build"
    (let [stored (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-project "the_project" (a-job "theJobId" "theProject" "theJob"))
                                                    (a-job-with-builds "theJobId" {:id 42
                                                                                   :number 2
                                                                                   :status "SUCCESS"
                                                                                   :startDate "20160410T041049+0000"
                                                                                   :finishDate "20160410T041100+0000"})
                                                    (capture-puts-to-buildviz-in stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"]))
        (is (= [["/builds/theProject%20theJob/2" {:start 1460261449000
                                                  :end 1460261460000
                                                  :outcome "pass"}]]
               @stored)))))

  (testing "should sync in ascending order by date"
    (let [stored (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-project "the_project"
                                                               (a-job "jobId1" "theProject" "job1")
                                                               (a-job "jobId2" "theProject" "job2"))
                                                    (a-job-with-builds "jobId1"
                                                                       {:id 12
                                                                        :number 11
                                                                        :startDate "20160410T000300+0000"
                                                                        :finishDate "20160410T000400+0000"}
                                                                       {:id 10
                                                                        :number 10
                                                                        :startDate "20160410T000000+0000"
                                                                        :finishDate "20160410T000100+0000"})
                                                    (a-job-with-builds "jobId2" {:id 20
                                                                                 :number 42
                                                                                 :startDate "20160410T000100+0000"
                                                                                 :finishDate "20160410T000200+0000"})
                                                    (capture-puts-to-buildviz-in stored))
        (with-out-str (sut/sync-jobs  (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"]))
        (is (= ["/builds/theProject%20job1/10"
                "/builds/theProject%20job2/42"
                "/builds/theProject%20job1/11"]
               (map first @stored))))))

  (testing "should stop at running build"
    (let [stored (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-project "the_project"
                                                               (a-job "jobId1" "theProject" "job1"))
                                                    (a-job-with-builds "jobId1"
                                                                       {:id 12
                                                                        :number 12
                                                                        :state "finished"
                                                                        :startDate "20160410T000400+0000"
                                                                        :finishDate "20160410T000500+0000"}
                                                                       {:id 11
                                                                        :number 11
                                                                        :state "running"
                                                                        :startDate "20160410T000200+0000"
                                                                        :finishDate "20160410T000300+0000"}
                                                                       {:id 10
                                                                        :number 10
                                                                        :state "finished"
                                                                        :startDate "20160410T000000+0000"
                                                                        :finishDate "20160410T000100+0000"})
                                                    (capture-puts-to-buildviz-in stored))
        (with-out-str (sut/sync-jobs  (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"]))
        (is (= ["/builds/theProject%20job1/10"]
               (map first @stored)))))))
