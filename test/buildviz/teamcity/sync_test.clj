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

(defn- a-build [job-id build]
  [[(format "http://teamcity:8000/httpAuth/app/rest/buildTypes/id:%s/builds/?fields=build(id,number,status,startDate,finishDate,revisions(revision(version,vcs-root-instance)))"
            job-id)
    (successful-json-response {:build [(merge {:revisions []}
                                              build)]})]
   [(format "http://teamcity:8000/httpAuth/app/rest/testOccurrences?locator=count:100,start:0,build:(id:%s)"
            (:id build))
    (successful-json-response {:testOccurrences []})]])

(defn- capture-puts-to-buildviz-in [map-ref]
  [[#"http://buildviz:8010/builds/(.+)/(.+)"
    (fn [req]
      (swap! map-ref #(assoc % (:uri req) (j/parse-string (slurp (:body req)) true)))
      {:status 200 :body ""})]])

(defn- serve-up [& routes]
  (->> routes
       (mapcat identity) ; flatten once
       (into {})))

(deftest test-main
  (testing "should sync a build"
    (let [stored (atom {})]
      (fake/with-fake-routes (serve-up (a-project "the_project" (a-job "theId" "theProject" "theJob"))
                                       (a-build "theId" {:id 42
                                                         :number 2
                                                         :status "SUCCESS"
                                                         :startDate "20160410T041049+0000"
                                                         :finishDate "20160410T041100+0000"})
                                       (capture-puts-to-buildviz-in stored))
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"]))
        (is (= {:start 1460261449000
                :end 1460261460000
                :outcome "pass"}
               (get @stored "/builds/theProject%20theJob/2")))))))
