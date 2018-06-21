(ns buildviz.teamcity.api
  (:require [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [uritemplate-clj.core :as templ]))

(def ^:private teamcity-user (System/getenv "TEAMCITY_USER"))
(def ^:private teamcity-password (System/getenv "TEAMCITY_PASSWORD"))
(def ^:private teamcity-basic-auth (when teamcity-user
                                     [teamcity-user teamcity-password]))

(defn- get-json [teamcity-url relative-url]
  (log/info (format "Retrieving %s" relative-url))
  (let [response (client/get (string/join [(url/with-plain-text-password teamcity-url)
                                           relative-url])
                             {:accept "application/json"
                              :client-params {"http.useragent" "buildviz (https://github.com/cburgmer/buildviz)"}
                              :basic-auth teamcity-basic-auth})]
    (log/info (format "Retrieved %s: %s" relative-url (:status response)))
    (j/parse-string (:body response) true)))

(defn get-jobs [teamcity-url project-id]
  (let [response (get-json teamcity-url (templ/uritemplate "/httpAuth/app/rest/projects{/project}"
                                                           {"project" project-id}))
        jobs (-> response
                 (get :buildTypes)
                 (get :buildType))
        sub-projects (->> response
                          :projects
                          :project
                          (map :id))]
    (concat jobs
            (mapcat #(get-jobs teamcity-url %) sub-projects))))


(def ^:private builds-paging-count 100)

(def ^:private build-fields ["id" "number" "status" "startDate" "finishDate"
                             "state" "revisions(revision(version,vcs-root-instance))"
                             "snapshot-dependencies(build(number,buildType(name,projectName)))"
                             "triggered"])

(defn- get-builds-from [teamcity-url job-id offset]
  (let [response (get-json teamcity-url
                           (templ/uritemplate "/httpAuth/app/rest/buildTypes/id:{job}/builds/?locator=count:{count},start:{offset}&fields=build({fields})"
                                              {"job" job-id
                                               "count" builds-paging-count
                                               "offset" offset
                                               "fields" build-fields}))
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
                           (templ/uritemplate "/httpAuth/app/rest/testOccurrences?locator=count:{count},start:{offset},build:(id:{build})"
                                              {"count" test-occurrence-paging-count
                                               "offset" offset
                                               "build" build-id}))
        test-occurrences (get response :testOccurrence)]
    (if (< (count test-occurrences) test-occurrence-paging-count)
      test-occurrences
      (let [next-offset (+ offset test-occurrence-paging-count)]
        (concat test-occurrences
                (get-test-report-from teamcity-url build-id next-offset))))))

(defn get-test-report [teamcity-url build-id]
  (get-test-report-from teamcity-url build-id 0))
