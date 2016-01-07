(ns buildviz.handler
  (:require [buildviz.controllers
             [builds
              :refer
              [get-build get-test-results store-build! store-test-results!]]
             [fail-phases :refer [get-fail-phases]]
             [flaky-testcases :refer [get-flaky-testclasses]]
             [job-runtime :refer [get-job-runtime]]
             [jobs :refer [get-jobs]]
             [pipeline-runtime :refer [get-pipeline-runtime]]
             [status :refer [get-status]]
             [testcases :refer [get-testcases]]
             [testclasses :refer [get-testclasses]]]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]
            [compojure.core :as compojure :refer :all]
            [ring.middleware
             [accept :as accept]
             [content-type :as content-type]
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

   (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build! build-results job build body))
   (GET "/builds/:job/:build" [job build] (get-build build-results job build))
   (PUT "/builds/:job/:build/testresults" [job build :as {body :body content-type :content-type}] (store-test-results! build-results job build body content-type))
   (GET "/builds/:job/:build/testresults" [job build :as {accept :accept}] (get-test-results build-results job build accept))

   (GET "/status" {} (get-status build-results pipeline-name))
   (GET "/jobs" {accept :accept query :query-params} (get-jobs build-results accept (from-timestamp query)))
   (GET "/jobs.csv" {query :query-params} (get-jobs build-results {:mime :csv} (from-timestamp query)))
   (GET "/jobruntime" {query :query-params} (get-job-runtime build-results (from-timestamp query)))
   (GET "/jobruntime.csv" {query :query-params} (get-job-runtime build-results (from-timestamp query)))
   (GET "/pipelineruntime" {accept :accept query :query-params} (get-pipeline-runtime build-results accept (from-timestamp query)))
   (GET "/pipelineruntime.csv" {query :query-params} (get-pipeline-runtime build-results {:mime :csv} (from-timestamp query)))
   (GET "/failphases" {accept :accept query :query-params} (get-fail-phases build-results accept (from-timestamp query)))
   (GET "/failphases.csv" {query :query-params} (get-fail-phases build-results {:mime :csv} (from-timestamp query)))
   (GET "/testcases" {accept :accept query :query-params} (get-testcases build-results accept (from-timestamp query)))
   (GET "/testcases.csv" {query :query-params} (get-testcases build-results {:mime :csv} (from-timestamp query)))
   (GET "/testclasses" {accept :accept query :query-params} (get-testclasses build-results accept (from-timestamp query)))
   (GET "/testclasses.csv" {query :query-params} (get-testclasses build-results {:mime :csv} (from-timestamp query)))
   (GET "/flakytestcases" {accept :accept query :query-params} (get-flaky-testclasses build-results accept (from-timestamp query)))
   (GET "/flakytestcases.csv" {query :query-params} (get-flaky-testclasses build-results {:mime :csv} (from-timestamp query)))))

(defn- wrap-build-results-not-modified [handler build-results]
  (fn [request]
    (http/not-modified-request handler (results/last-modified build-results) request)))

(defn create-app [build-results pipeline-name]
  (-> (app-routes build-results pipeline-name)
      (wrap-build-results-not-modified build-results)
      params/wrap-params
      (http/wrap-json-body)
      (accept/wrap-accept {:mime ["application/json" :as :json,
                                  "application/xml" "text/xml" :as :xml
                                  "text/plain" :as :plain]})
      (resources/wrap-resource "public")
      content-type/wrap-content-type
      not-modified/wrap-not-modified))
