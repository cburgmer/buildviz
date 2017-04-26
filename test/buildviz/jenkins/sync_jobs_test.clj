(ns buildviz.jenkins.sync-jobs-test
  (:require [buildviz.jenkins.sync :as sut]
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

(defn- serve-up [& routes]
  (->> routes
       (mapcat identity)
       (into {})))

(def beginning-of-2016 (t/date-midnight 2016))


(deftest test-sync-jobs
  (testing "should handle empty jobs"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation (serve-up (a-view))
        (with-out-str (sut/sync-jobs (url/url "http://jenkins:4321") (url/url "http://buildviz:8010") beginning-of-2016)))
      (is (= []
             @store)))))
