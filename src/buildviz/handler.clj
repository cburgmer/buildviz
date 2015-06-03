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

(defn- avg [series]
  (/ (reduce + series) (count series)))

(defn- duration-for [build]
  (if (and (contains? build :end) (contains? build :end))
    (- (build :end) (build :start))))

(defn- average-runtime-for [summary build-data-entries]
  (let [runtimes (filter (complement nil?) (map duration-for build-data-entries))]
    (if (not (empty? runtimes))
      (assoc summary :averageRuntime (avg runtimes))
      summary)))

(defn- total-count-for [summary build-data-entries]
  (assoc summary :totalCount (count build-data-entries)))

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
  (let [build-data-entries (vals (@builds job))]
    (-> {}
        (average-runtime-for build-data-entries)
        (total-count-for build-data-entries)
        (error-count-for build-data-entries)
        (flaky-runs-for build-data-entries))))

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
