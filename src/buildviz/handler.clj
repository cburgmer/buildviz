(ns buildviz.handler
  (:use compojure.core
        ring.middleware.json
        ring.middleware.resource
        ring.middleware.content-type
        ring.middleware.not-modified
        ring.middleware.accept
        ring.util.response
        [clojure.string :only (join escape)])
  (:require [compojure.handler :as handler]
            [buildviz.csv :as csv]
            [buildviz.jobinfo :as jobinfo]
            [buildviz.pipelineinfo :as pipelineinfo]
            [buildviz.testsuites :as testsuites]))

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

(defn- get-test-results [job build accept]
  (if-let [job-results (@test-results job)]
    (if-let [content (job-results build)]
      (if (= (:mime accept) :json)
        {:body (testsuites/testsuites-for content)}
        {:body content
         :headers {"Content-Type" "application/xml;charset=UTF-8"}})
      {:status 404})
    {:status 404}))

;; jobs

(defn- average-runtime-for [build-data-entries]
  (if-let [avg-runtime (jobinfo/average-runtime build-data-entries)]
    {:averageRuntime avg-runtime}))

(defn- total-count-for [build-data-entries]
  {:totalCount (count build-data-entries)})

(defn- failed-count-for [build-data-entries]
  (if-let [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:failedCount (jobinfo/fail-count builds)}))

(defn- flaky-count-for [build-data-entries]
  (if-let [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:flakyCount (jobinfo/flaky-build-count builds)}))

(defn- summary-for [job]
  (let [build-data-entries (vals (@builds job))]
    (merge (average-runtime-for build-data-entries)
           (total-count-for build-data-entries)
           (failed-count-for build-data-entries)
           (flaky-count-for build-data-entries))))

(defn- get-jobs [accept]
  (let [jobNames (keys @builds)
        buildSummaries (map summary-for jobNames)
        buildSummary (zipmap jobNames buildSummaries)]
    (if (= (:mime accept) :json)
      {:body buildSummary}
      {:body (csv/export-table ["job" "averageRuntime" "totalCount" "failedCount" "flakyCount"]
                               (map (fn [[job-name job]] [job-name
                                                          (csv/format-duration (:averageRuntime job))
                                                          (:totalCount job)
                                                          (:failedCount job)
                                                          (:flakyCount job)])
                                    buildSummary))})))

;; fail phases

(defn- all-builds-in-order []
  (mapcat (fn [[job builds]]
            (map #(assoc % :job job) (vals builds)))
          @builds))

(defn- get-fail-phases [accept]
  (let [annotated-builds-in-order (sort-by :end (all-builds-in-order))
        fail-phases (pipelineinfo/pipeline-fail-phases annotated-builds-in-order)]
    (if (= (:mime accept) :json)
      {:body fail-phases}
      {:body (csv/export-table ["start" "end" "culprits"]
                               (map (fn [{start :start end :end culprits :culprits}]
                                      [(csv/format-timestamp start)
                                       (csv/format-timestamp end)
                                       (join "|" culprits)])
                                    fail-phases))})))

;; failures

(defn- failures-for [job]
  (let [build-data-entries (vals (@builds job))]
    (merge (if-let [test-results (@test-results job)]
             (let [test-runs (map testsuites/testsuites-for (vals test-results))]
               {:children (testsuites/accumulate-testsuite-failures test-runs)})
             {})
           (failed-count-for build-data-entries))))

(defn- get-failures []
  (let [job-names (keys @builds)
        failures (map failures-for job-names)]
    {:body (zipmap job-names failures)}))

;; testsuites

(defn- test-runs [job]
  (let [test-results (@test-results job)]
    (map testsuites/testsuites-for (vals test-results))))

(defn- testsuites-for [job]
  {:children (testsuites/average-testsuite-runtime (test-runs job))})

(defn- serialize-nested-testsuites [testsuite-id]
  (join ": " testsuite-id))

(defn- flat-test-runtimes [job]
  (->> (testsuites/average-testsuite-runtime-as-list (test-runs job))
       (map (fn [{testsuite :testsuite classname :classname name :name average-runtime :averageRuntime}]
              [average-runtime
               job
               (serialize-nested-testsuites testsuite)
               classname
               name]))))

(defn- has-testsuites? [job]
  (some? (@test-results job)))

(defn- get-testsuites [accept]
  (let [job-names (filter has-testsuites? (keys @builds))]
    (if (= (:mime accept) :json)
      {:body (zipmap job-names (map testsuites-for job-names))}
      {:body (csv/export-table ["averageRuntime" "job" "testsuite" "classname" "name"]
                               (mapcat flat-test-runtimes
                                       job-names))})))

;; app

(defroutes app-routes
  (GET "/" [] (redirect "/index.html"))

  (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build! job build body))
  (GET "/builds/:job/:build" [job build] (get-build job build))
  (PUT "/builds/:job/:build/testresults" [job build :as {body :body}] (store-test-results! job build body))
  (GET "/builds/:job/:build/testresults" [job build :as {accept :accept}] (get-test-results job build accept))

  (GET "/jobs" {accept :accept} (get-jobs accept))
  (GET "/failphases" {accept :accept} (get-fail-phases accept))
  (GET "/failures" [] (get-failures))
  (GET "/testsuites" {accept :accept} (get-testsuites accept)))

(defn- wrap-log-request [handler]
  (fn [req]
    (println (:request-method req) (:uri req))
    (handler req)))

(def app
  (-> app-routes
      wrap-log-request
      wrap-json-response
      (wrap-json-body {:keywords? true})
      (wrap-accept {:mime ["application/json" :as :json,
                           "application/xml" "text/xml" :as :xml
                           "text/plain" :as :plain]})
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))
