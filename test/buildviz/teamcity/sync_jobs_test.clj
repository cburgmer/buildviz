(ns buildviz.teamcity.sync-jobs-test
  (:require [buildviz.teamcity.sync-jobs :as sut]
            [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.fake :as fake]
            [clj-time
             [coerce :as tc]
             [core :as t]]
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
  (let [job-builds [(format "http://teamcity:8000/httpAuth/app/rest/buildTypes/id:%s/builds/?locator=count:100,start:0&fields=build(id,number,status,startDate,finishDate,state,revisions%%28revision%%28version%%2Cvcs-root-instance%%29%%29,snapshot-dependencies%%28build%%28number%%2CbuildType%%28name%%2CprojectName%%29%%29%%29,triggered)"
                            job-id)
                    (successful-json-response {:build (map #(merge {:revisions []
                                                                    :status "SUCCESS"
                                                                    :state "finished"}
                                                                   %) builds)})]
        testresults (map (fn [build]
                           [(format "http://teamcity:8000/httpAuth/app/rest/testOccurrences?locator=count:10000,start:0,build:(id:%s)"
                                    (:id build))
                            (successful-json-response {:testOccurrence []})]) builds)]
    (cons job-builds testresults)))

(defn- a-job-with-tests [job-id build tests]
  (let [job-builds [(format "http://teamcity:8000/httpAuth/app/rest/buildTypes/id:%s/builds/?locator=count:100,start:0&fields=build(id,number,status,startDate,finishDate,state,revisions%%28revision%%28version%%2Cvcs-root-instance%%29%%29,snapshot-dependencies%%28build%%28number%%2CbuildType%%28name%%2CprojectName%%29%%29%%29,triggered)"
                            job-id)
                    (successful-json-response {:build [(merge {:revisions []
                                                               :status "SUCCESS"
                                                               :state "finished"}
                                                              build)]})]
        testresults [[(format "http://teamcity:8000/httpAuth/app/rest/testOccurrences?locator=count:10000,start:0,build:(id:%s)"
                              (:id build))
                      (successful-json-response {:testOccurrence tests})]]]
    (cons job-builds testresults)))

(def beginning-of-2016 (t/date-time 2016 1 1))

(defn- provide-buildviz-and-capture-puts [latest-build-start map-ref]
  [[#"http://buildviz:8010/builds/([^/]+)/([^/]+)"
    (fn [req]
      (swap! map-ref #(conj % [(:uri req)
                               (j/parse-string (slurp (:body req)) true)]))
      {:status 200 :body ""})]
   [#"http://buildviz:8010/builds/([^/]+)/([^/]+)/testresults"
    (fn [req]
      (swap! map-ref #(conj % [(:uri req)
                               (j/parse-string (slurp (:body req)) true)]))
      {:status 204 :body ""})]
   ["http://buildviz:8010/status"
    (successful-json-response (cond-> {}
                                latest-build-start (assoc :latestBuildStart (tc/to-long latest-build-start))))]])

(defn- fail-buildviz-on-put-testresult []
  [[#"http://buildviz:8010/builds/([^/]+)/([^/]+)"
    (fn [req] {:status 200 :body ""})]
   [#"http://buildviz:8010/builds/([^/]+)/([^/]+)/testresults"
    (fn [req] {:status 400 :body ""})]
   ["http://buildviz:8010/status"
    (successful-json-response {})]])

(defn- serve-up [& routes]
  (->> routes
       (mapcat identity) ; flatten once
       (into {})))

(deftest test-teamcity-sync-jobs
  (testing "should sync a build"
    (let [stored (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-project "the_project" (a-job "theJobId" "theProject" "theJob #1"))
                                                    (a-job-with-builds "theJobId" {:id 42
                                                                                   :number 2
                                                                                   :status "SUCCESS"
                                                                                   :startDate "20160410T041049+0000"
                                                                                   :finishDate "20160410T041100+0000"})
                                                    (provide-buildviz-and-capture-puts beginning-of-2016 stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"] beginning-of-2016 nil))
        (is (= [["/builds/theProject%20theJob%20%231/2" {:start 1460261449000
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
                                                    (provide-buildviz-and-capture-puts beginning-of-2016 stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"] beginning-of-2016 nil))
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
                                                    (provide-buildviz-and-capture-puts beginning-of-2016 stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"] beginning-of-2016 nil))
        (is (= ["/builds/theProject%20job1/10"]
               (map first @stored))))))

  (testing "should resume where left off"
    (let [latest-build-start (t/from-time-zone (t/date-time 2016 4 10 0 2 0) t/utc)
          stored (atom [])]
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
                                                                        :state "finished"
                                                                        :startDate "20160410T000200+0000"
                                                                        :finishDate "20160410T000300+0000"}
                                                                       {:id 10
                                                                        :number 10
                                                                        :state "finished"
                                                                        :startDate "20160410T000000+0000"
                                                                        :finishDate "20160410T000100+0000"})
                                                    (provide-buildviz-and-capture-puts latest-build-start stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"] beginning-of-2016 nil))
        (is (= ["/builds/theProject%20job1/11"
                "/builds/theProject%20job1/12"]
               (map first @stored))))))

  (testing "should sync from initial state"
    (let [latest-build-start nil
          stored (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-project "the_project"
                                                               (a-job "jobId1" "theProject" "job1"))
                                                    (a-job-with-builds "jobId1"
                                                                       {:id 10
                                                                        :number 10
                                                                        :startDate "20160410T000000+0000"
                                                                        :finishDate "20160410T000100+0000"})
                                                    (provide-buildviz-and-capture-puts latest-build-start stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"] beginning-of-2016 nil))
        (is (= ["/builds/theProject%20job1/10"]
               (map first @stored))))))

  (testing "should sync from given start date"
    (let [build-start (t/from-time-zone (t/date-time 2016 4 10 0 2 0) t/utc)
          stored (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-project "the_project"
                                                               (a-job "jobId1" "theProject" "job1"))
                                                    (a-job-with-builds "jobId1"
                                                                       {:id 12
                                                                        :number 12
                                                                        :startDate "20160410T000400+0000"
                                                                        :finishDate "20160410T000500+0000"}
                                                                       {:id 11
                                                                        :number 11
                                                                        :startDate "20160410T000200+0000"
                                                                        :finishDate "20160410T000300+0000"}
                                                                       {:id 10
                                                                        :number 10
                                                                        :startDate "20160410T000000+0000"
                                                                        :finishDate "20160410T000100+0000"})
                                                    (provide-buildviz-and-capture-puts beginning-of-2016 stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"] beginning-of-2016 build-start))
        (is (= ["/builds/theProject%20job1/11"
                "/builds/theProject%20job1/12"]
               (map first @stored))))))

  (testing "should sync test results"
    (let [latest-build-start nil
          stored (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-project "the_project"
                                                               (a-job "jobId1" "theProject" "job1"))
                                                    (a-job-with-tests "jobId1"
                                                                      {:id 10
                                                                       :number 10
                                                                       :startDate "20160410T000000+0000"
                                                                       :finishDate "20160410T000100+0000"}
                                                                      [{:name "suite: class.the test"
                                                                        :status "SUCCESS"
                                                                        :duration 42}])
                                                    (provide-buildviz-and-capture-puts latest-build-start stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"] beginning-of-2016 nil)))
      (is (= ["/builds/theProject%20job1/10/testresults" [{:name "suite"
                                                           :children [{:name "the test"
                                                                       :classname "class"
                                                                       :status "pass"
                                                                       :runtime 42}]}]]
             (nth @stored 1)))))

  (testing "should handle error when syncing test results"
    (fake/with-fake-routes-in-isolation (serve-up (a-project "the_project"
                                                             (a-job "jobId1" "theProject" "job1"))
                                                  (a-job-with-tests "jobId1"
                                                                    {:id 10
                                                                     :number 10
                                                                     :startDate "20160410T000000+0000"
                                                                     :finishDate "20160410T000100+0000"}
                                                                    [{:name "suite: class.the test"
                                                                      :status "SUCCESS"
                                                                      :duration 42}])
                                                  (fail-buildviz-on-put-testresult))
      (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"] beginning-of-2016 nil)))))
