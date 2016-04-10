(ns buildviz.teamcity.api
  (:require [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- get-json [teamcity-url path-template & path-params]
  (let [relative-url (apply format (cons path-template path-params))]
    (log/info (format "Retrieving %s" relative-url))
    (j/parse-string (:body (client/get (string/join [(url/with-plain-text-password teamcity-url)
                                                     relative-url])
                                       {:accept "application/json"})) true)))

(defn get-jobs [teamcity-url project-name]
  (let [response (get-json teamcity-url "/httpAuth/app/rest/projects/%s" project-name)]
    (-> response
        (get :buildTypes)
        (get :buildType))))

(defn get-builds [teamcity-url job-id]
  (let [response (get-json teamcity-url
                           "/httpAuth/app/rest/buildTypes/id:%s/builds/?fields=build(id,number,status,startDate,finishDate,revisions(revision(version,vcs-root-instance)))"
                           job-id)]
    (get response :build)))

(def ^:private test-occurrence-paging-count 100)

(defn get-test-report-from [teamcity-url build-id offset]
  (let [response (get-json teamcity-url
                           "/httpAuth/app/rest/testOccurrences?locator=count:%s,start:%s,build:(id:%s)"
                           test-occurrence-paging-count offset build-id)
        test-occurrences (get response :testOccurrence)]
    (if (< (count test-occurrences) test-occurrence-paging-count)
      test-occurrences
      (let [next-offset (+ offset test-occurrence-paging-count)]
        (concat test-occurrences
                (lazy-seq (get-test-report-from teamcity-url build-id next-offset)))))))

(defn get-test-report [teamcity-url build-id]
  (get-test-report-from teamcity-url build-id 0))
