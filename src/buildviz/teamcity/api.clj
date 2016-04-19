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


(def ^:private builds-paging-count 100)

(defn- get-builds-from [teamcity-url job-id offset]
  (let [response (get-json teamcity-url
                           "/httpAuth/app/rest/buildTypes/id:%s/builds/?locator=count:%s,start:%s&fields=build(id,number,status,startDate,finishDate,state,revisions(revision(version,vcs-root-instance)))"
                           job-id builds-paging-count offset)
        builds (get response :build)]
    (if (< (count builds) builds-paging-count)
      builds
      (let [next-offset (+ offset builds-paging-count)]
        (concat builds
                (get-builds-from teamcity-url job-id next-offset))))))

(defn get-builds [teamcity-url job-id]
  (get-builds-from teamcity-url job-id 0))


(def ^:private test-occurrence-paging-count 10000)

(defn- get-test-report-from [teamcity-url build-id offset]
  (let [response (get-json teamcity-url
                           "/httpAuth/app/rest/testOccurrences?locator=count:%s,start:%s,build:(id:%s)"
                           test-occurrence-paging-count offset build-id)
        test-occurrences (get response :testOccurrence)]
    (if (< (count test-occurrences) test-occurrence-paging-count)
      test-occurrences
      (let [next-offset (+ offset test-occurrence-paging-count)]
        (concat test-occurrences
                (get-test-report-from teamcity-url build-id next-offset))))))

(defn get-test-report [teamcity-url build-id]
  (get-test-report-from teamcity-url build-id 0))
