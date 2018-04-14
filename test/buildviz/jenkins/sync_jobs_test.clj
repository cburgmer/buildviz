(ns buildviz.jenkins.sync-jobs-test
  (:require [buildviz.jenkins.sync-jobs :as sut]
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

(defn- a-job [name]
  {:name name})

(defn- a-view [& jobs]
  [["http://jenkins:4321/api/json"
    (successful-json-response {:jobs jobs})]])

(defn- a-job-with-builds [job-name & builds]
  (let [job-builds [(format "http://jenkins:4321/job/%s/api/json?tree=allBuilds%%5Bnumber,timestamp,duration,result,actions%%5BlastBuiltRevision%%5BSHA1%%5D,remoteUrls,parameters%%5Bname,value%%5D,causes%%5BupstreamProject,upstreamBuild,userId%%5D%%5D%%5D%%7B0,10%%7D"
                            job-name)
                    (successful-json-response {:allBuilds builds})]
        test-results (map (fn [build]
                            [(format "http://jenkins:4321/job/%s/%s/testReport/api/json" job-name (:number build))
                             (fn [_] {:status 404 :body ""})]) builds)]
    (cons job-builds test-results)))

(defn- provide-buildviz-and-capture-puts [map-ref]
  [[#"http://buildviz:8010/builds/(.+)/(.+)"
    (fn [req]
      (swap! map-ref #(conj % [(:uri req)
                               (j/parse-string (slurp (:body req)) true)]))
      {:status 200 :body ""})]])

(defn- serve-up [& routes]
  (->> routes
       (mapcat identity)
       (into {})))

(def beginning-of-2016 (t/date-time 2016 1 1))


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
             @store))))

  (testing "should sync a simple build"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-view (a-job "some_job"))
                                                    (a-job-with-builds "some_job" {:number "21"
                                                                                   :timestamp 1493201298062
                                                                                   :duration 10200
                                                                                   :result "SUCCESS"})
                                                    (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-jobs (url/url "http://jenkins:4321") (url/url "http://buildviz:8010") beginning-of-2016)))
      (is (= [["/builds/some_job/21" {:start 1493201298062
                                      :end 1493201308262
                                      :outcome "pass"}]]
             @store))))

  (testing "should omit build trigger if triggered by user due to temporal disconnect"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-view (a-job "some_job"))
                                                    (a-job-with-builds "some_job" {:number "21"
                                                                                   :timestamp 1493201298062
                                                                                   :duration 10200
                                                                                   :result "SUCCESS"
                                                                                   :actions [{:causes [{:upstreamProject "the_upstream"
                                                                                                        :upstreamBuild "33"}]}
                                                                                             {:causes [{:userId "the_user"}]}]})
                                                    (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-jobs (url/url "http://jenkins:4321") (url/url "http://buildviz:8010") beginning-of-2016)))
      (is (nil? (get (first (map last @store))
                     :triggeredBy))))))
