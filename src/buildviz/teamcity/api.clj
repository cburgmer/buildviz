(ns buildviz.teamcity.api
  (:require [cheshire.core :as j]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- get-json [teamcity-url relative-url]
  (log/info (format "Retrieving %s" relative-url))
  (j/parse-string (:body (client/get (string/join [teamcity-url relative-url])
                                     {:accept "application/json"})) true))

(defn get-jobs [teamcity-url project-name]
  (let [response (get-json teamcity-url
                           (format "/httpAuth/app/rest/projects/%s" project-name))]
    (map :id (-> response
                 (get :buildTypes)
                 (get :buildType )))))

(defn get-builds [teamcity-url job-id]
  (let [response (get-json teamcity-url
                           (format "/httpAuth/app/rest/buildTypes/id:%s/builds/?fields=build(id,number,status,startDate,finishDate)" job-id))]
    (get response :build)))

(defn get-test-report [teamcity-url build-id]
  (let [response (get-json teamcity-url
                           (format "/httpAuth/app/rest/testOccurrences?locator=build:(id:%s)" build-id))]
    (get response :testOccurrence)))
