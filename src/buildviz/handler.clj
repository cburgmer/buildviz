(ns buildviz.handler
  (:use compojure.core
        ring.middleware.json
        ring.middleware.resource
        ring.middleware.content-type
        ring.middleware.not-modified)
  (:require [compojure.handler :as handler]
             [buildviz.jobinfo :as jobinfo]))

(def builds (atom {}))
(def test-results (atom {}))

(defn- job-entry [job]
  (if (contains? @builds job)
    (@builds job)
    {}))

(defn- test-results-entry [job]
  (if (contains? @test-results job)
    (@test-results job)
    {}))

(defn- store-build! [job build build-data]
  (let [entry (job-entry job)
        updated-entry (assoc entry build build-data)]
    (swap! builds assoc job updated-entry)
    {:body build-data}))

(defn- get-build [job build]
  (if (contains? @builds job)
    (if-let [build-data ((@builds job) build)]
      {:body build-data}
      {:status 404})
    {:status 404}))

(defn- store-test-results! [job build body]
  (let [content (slurp body)
        entry (test-results-entry job)
        updated-entry (assoc entry build content)]
    (swap! test-results assoc job updated-entry))
  {:status 204})

(defn- get-test-results [job build]
  (if-let [job-results (@test-results job)]
    (if-let [content (job-results build)]
      {:body content
       :headers {"Content-Type" "application/xml;charset=UTF-8"}}
      {:status 404})
    {:status 404}))

;; summary

(defn- average-runtime-for [summary build-data-entries]
  (if-let [avg-runtime (jobinfo/average-runtime build-data-entries)]
    (assoc summary :averageRuntime avg-runtime)
    summary))

(defn- total-count-for [summary build-data-entries]
  (assoc summary :totalCount (count build-data-entries)))

(defn- failed-count-for [summary build-data-entries]
  (if-let [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    (assoc summary :failedCount (jobinfo/fail-count builds))
    summary))

(defn- flaky-count-for [summary build-data-entries]
  (if-let [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    (assoc summary :flakyCount (jobinfo/flaky-build-count builds))
    summary))

(defn- summary-for [job]
  (let [build-data-entries (vals (@builds job))]
    (-> {}
        (average-runtime-for build-data-entries)
        (total-count-for build-data-entries)
        (failed-count-for build-data-entries)
        (flaky-count-for build-data-entries))))

(defn- get-pipeline []
  (let [jobNames (keys @builds)
        buildSummaries (map summary-for jobNames)
        buildSummary (zipmap jobNames buildSummaries)]
    {:body buildSummary}))

;; app

(defroutes app-routes
  (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build! job build body))
  (GET "/builds/:job/:build" [job build] (get-build job build))
  (PUT "/builds/:job/:build/testresults" [job build :as {body :body}] (store-test-results! job build body))
  (GET "/builds/:job/:build/testresults" [job build] (get-test-results job build))

  (GET "/pipeline" [] (get-pipeline)))

(defn- wrap-log-request [handler]
  (fn [req]
    (println (:request-method req) (:uri req))
    (handler req)))

(def app
  (-> app-routes
      wrap-log-request
      wrap-json-response
      (wrap-json-body {:keywords? true})
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))
