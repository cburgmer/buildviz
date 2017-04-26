(ns buildviz.jenkins.sync-jobs-test
  (:require [buildviz.jenkins.sync-jobs :as sut]
            [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.fake :as fake]
            [clj-time.core :as t]
            [clojure.test :refer :all]))

(defn- successful-json-response [body]
  (fn [_] {:status 200
           :body (j/generate-string body)}))

(defn- a-job [name]
  {:name name})

(defn- a-view [& jobs]
  [["http://jenkins:4321/api/json"
    (successful-json-response {:jobs jobs})]])

(defn- a-job-with-builds [job-name & builds]
  (let [job-builds [(format "http://jenkins:4321/job/%s/api/json?tree=allBuilds%%5Bnumber,timestamp,duration,result,actions%%5BlastBuiltRevision%%5BSHA1%%5D,remoteUrls,parameters%%5Bname,value%%5D,causes%%5BupstreamProject,upstreamBuild%%5D%%5D%%5D%%7B0,10%%7D"
                            job-name)
                    (successful-json-response {:allBuilds []})]]
    [job-builds]))


(defn- serve-up [& routes]
  (->> routes
       (mapcat identity)
       (into {})))

(def beginning-of-2016 (t/date-midnight 2016))


(deftest test-sync-jobs
  (testing "should handle no jobs "
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-view))
        (with-out-str (sut/sync-jobs (url/url "http://jenkins:4321") (url/url "http://buildviz:8010") beginning-of-2016)))
      (is (= []
             @store))))

  (testing "should handle no builds"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-view (a-job "some_job"))
                                                    (a-job-with-builds "some_job"))
        (with-out-str (sut/sync-jobs (url/url "http://jenkins:4321") (url/url "http://buildviz:8010") beginning-of-2016)))
      (is (= []
             @store)))))
