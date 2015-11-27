(ns buildviz.handler
  (:require [buildviz.controllers
             [builds :as builds]
             [fail-phases :as fail-phases]
             [failures :as failures]
             [flaky-testcases :as flaky-testcases]
             [jobs :as jobs]
             [pipeline-runtime :as pipeline-runtime]
             [status :as status]
             [testcases :as testcases]
             [testclasses :as testclasses]]
            [buildviz.data.results :as results]
            [buildviz.http :as http]
            [compojure.core :as compojure :refer :all]
            [ring.middleware
             [accept :as accept]
             [content-type :as content-type]
             [json :as json]
             [not-modified :as not-modified]
             [params :as params]
             [resource :as resources]]
            [ring.util.response :as response]))

(defn- from-timestamp [{from "from"}]
  (when from
    (Long. from)))

(defn- app-routes [build-results pipeline-name]
  (compojure/routes
   (GET "/" [] (response/redirect "/index.html"))

   (PUT "/builds/:job/:build" [job build :as {body :body}] (builds/store-build! build-results job build body))
   (GET "/builds/:job/:build" [job build] (builds/get-build build-results job build))
   (PUT "/builds/:job/:build/testresults" [job build :as {body :body}] (builds/store-test-results! build-results job build body))
   (GET "/builds/:job/:build/testresults" [job build :as {accept :accept}] (builds/get-test-results build-results job build accept))

   (GET "/status" {} (status/get-status build-results pipeline-name))
   (GET "/jobs" {accept :accept query :query-params} (jobs/get-jobs build-results accept (from-timestamp query)))
   (GET "/jobs.csv" {query :query-params} (jobs/get-jobs build-results {:mime :csv} (from-timestamp query)))
   (GET "/pipelineruntime" {query :query-params} (pipeline-runtime/get-pipeline-runtime build-results (from-timestamp query)))
   (GET "/pipelineruntime.csv" {query :query-params} (pipeline-runtime/get-pipeline-runtime build-results (from-timestamp query)))
   (GET "/failphases" {accept :accept query :query-params} (fail-phases/get-fail-phases build-results accept (from-timestamp query)))
   (GET "/failphases.csv" {query :query-params} (fail-phases/get-fail-phases build-results {:mime :csv} (from-timestamp query)))
   (GET "/failures" {accept :accept query :query-params} (failures/get-failures build-results accept (from-timestamp query)))
   (GET "/failures.csv" {query :query-params} (failures/get-failures build-results {:mime :csv} (from-timestamp query)))
   (GET "/testcases" {accept :accept query :query-params} (testcases/get-testcases build-results accept (from-timestamp query)))
   (GET "/testcases.csv" {query :query-params} (testcases/get-testcases build-results {:mime :csv} (from-timestamp query)))
   (GET "/testclasses" {accept :accept query :query-params} (testclasses/get-testclasses build-results accept (from-timestamp query)))
   (GET "/testclasses.csv" {query :query-params} (testclasses/get-testclasses build-results {:mime :csv} (from-timestamp query)))
   (GET "/flakytestcases" {query :query-params} (flaky-testcases/get-flaky-testclasses build-results (from-timestamp query)))
   (GET "/flakytestcases.csv" {query :query-params} (flaky-testcases/get-flaky-testclasses build-results (from-timestamp query)))))

(defn wrap-build-results-not-modified [handler build-results]
  (fn [request]
    (http/not-modified-request handler (results/last-modified build-results) request)))

(defn create-app [build-results pipeline-name]
  (-> (app-routes build-results pipeline-name)
      (wrap-build-results-not-modified build-results)
      params/wrap-params
      json/wrap-json-response
      (json/wrap-json-body {:keywords? true})
      (accept/wrap-accept {:mime ["application/json" :as :json,
                                  "application/xml" "text/xml" :as :xml
                                  "text/plain" :as :plain]})
      (resources/wrap-resource "public")
      content-type/wrap-content-type
      not-modified/wrap-not-modified))
