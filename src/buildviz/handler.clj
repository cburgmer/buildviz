(ns buildviz.handler
  (:use compojure.core
        ring.middleware.json
        ring.middleware.resource
        ring.middleware.content-type
        ring.middleware.not-modified)
  (:require [compojure.handler :as handler]
             [buildviz.jobinfo :as jobinfo]))

(def builds (atom {}))

(defn- job-entry [job]
  (if (contains? @builds job)
    (@builds job)
    {}))

(defn- store-build! [job build buildData]
  (let [entry (job-entry job)
        updatedEntry (assoc entry build buildData)]
    (swap! builds assoc job updatedEntry)
    {:body buildData}))

(defn- get-build [job build]
  (if (contains? @builds job)
    (if-let [buildData ((@builds job) build)]
      {:body buildData}
      {:status 404})
    {:status 404}))

(defn- avg [series]
  (/ (reduce + series) (count series)))

(defn- duration-for [build]
  (if (and (contains? build :end) (contains? build :end))
    (- (build :end) (build :start))))

(defn- average-runtime-for [summary buildDataEntries]
  (let [runtimes (filter (complement nil?) (map duration-for buildDataEntries))]
    (if (not (empty? runtimes))
      (assoc summary :averageRuntime (avg runtimes))
      summary)))

(defn- total-count-for [summary buildDataEntries]
  (assoc summary :totalCount (count buildDataEntries)))

(defn- builds-with-outcome [build-data-entries]
  (filter #(contains? % :outcome) build-data-entries))

(defn- error-count-for [summary build-data-entries]
  (if-let [builds (seq (builds-with-outcome build-data-entries))]
    (assoc summary :failedCount (count (filter #(= "fail" (:outcome %)) builds)))
    summary))

(defn- flaky-runs-for [summary build-data-entries]
  (if-let [builds (seq (builds-with-outcome build-data-entries))]
    (assoc summary :flakyCount (jobinfo/flaky-build-count builds))
    summary))

(defn- summary-for [job]
  (let [buildDataEntries (vals (@builds job))]
    (-> {}
        (average-runtime-for buildDataEntries)
        (total-count-for buildDataEntries)
        (error-count-for buildDataEntries)
        (flaky-runs-for buildDataEntries))))

(defn- get-pipeline []
  (let [jobNames (keys @builds)
        buildSummaries (map summary-for jobNames)
        buildSummary (zipmap jobNames buildSummaries)]
    {:body buildSummary}))

(defroutes app-routes
  (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build! job build body))
  (GET "/builds/:job/:build" [job build] (get-build job build))

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
