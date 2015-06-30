(ns buildviz.handler
  (:use compojure.core
        ring.middleware.json
        ring.middleware.resource
        ring.middleware.content-type
        ring.middleware.not-modified
        ring.middleware.accept
        ring.util.response)
  (:require [compojure.handler :as handler]
            [buildviz.jobinfo :as jobinfo]
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

;; pipeline

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

(defn- get-pipeline []
  (let [jobNames (keys @builds)
        buildSummaries (map summary-for jobNames)
        buildSummary (zipmap jobNames buildSummaries)]
    {:body buildSummary}))

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

(defn- testsuites-for [job]
  (let [test-results (@test-results job)
        test-runs (map testsuites/testsuites-for (vals test-results))]
    {:children (testsuites/average-testsuite-runtime test-runs)}))

(defn- has-testsuites? [job]
  (some? (@test-results job)))

(defn- get-testsuites []
  (let [job-names (filter has-testsuites? (keys @builds))]
    {:body (zipmap job-names (map testsuites-for job-names))}))

;; app

(defroutes app-routes
  (GET "/" [] (redirect "/index.html"))

  (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build! job build body))
  (GET "/builds/:job/:build" [job build] (get-build job build))
  (PUT "/builds/:job/:build/testresults" [job build :as {body :body}] (store-test-results! job build body))
  (GET "/builds/:job/:build/testresults" [job build :as {accept :accept}] (get-test-results job build accept))

  (GET "/pipeline" [] (get-pipeline))
  (GET "/failures" [] (get-failures))
  (GET "/testsuites" [] (get-testsuites)))

(defn- wrap-log-request [handler]
  (fn [req]
    (println (:request-method req) (:uri req))
    (handler req)))

(def app
  (-> app-routes
      wrap-log-request
      wrap-json-response
      (wrap-json-body {:keywords? true})
      (wrap-accept {:mime ["application/json" :as :json, "application/xml" "text/xml" :as :xml]})
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))
