(ns buildviz.go.api
  (:require [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-time
             [coerce :as tc]
             [format :as tf]]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [uritemplate-clj.core :as templ]))

(import com.fasterxml.jackson.core.JsonParseException)

(def ^:private gocd-user (System/getenv "GOCD_USER"))
(def ^:private gocd-password (System/getenv "GOCD_PASSWORD"))
(def ^:private gocd-basic-auth (when gocd-user
                                 [gocd-user gocd-password]))

(defn- absolute-url-for [go-url relative-url]
  (str/join [go-url relative-url]))

(defn- get-plain [go-url relative-url]
  (log/info (format "Retrieving %s" relative-url))
  (let [response (client/get (absolute-url-for (url/with-plain-text-password go-url) relative-url)
                             {:client-params {"http.useragent" "buildviz (https://github.com/cburgmer/buildviz)"}
                              :basic-auth gocd-basic-auth})]
    (log/info (format "Retrieved %s: %s" relative-url (:status response)))
    (:body response)))

(defn- get-json [go-url relative-url]
  (j/parse-string (get-plain go-url relative-url)
                  true))


;; /api/jobs/%d.xml

(defn- handle-missing-start-time-when-cancelled [build-start-time build-end-time]
  (if (nil? build-start-time)
    build-end-time
    build-start-time))

(defn- build-times [start-time end-time]
  (if-not (nil? end-time)
    {:start (handle-missing-start-time-when-cancelled start-time end-time)
     :end end-time}
    {}))

(defn- parse-datetime [value]
  (tc/to-long (tf/parse value)))

(defn- property-value-for [properties name]
  (some->> properties
           (filter #(and (= :property (:tag %))
                         (= name (:name (:attrs %)))))
           first
           :content
           first))

(defn- parse-build-properties [properties]
  (let [root (xml/parse-str properties)
        properties (->> (:content root)
                        (filter #(= :properties (:tag %)))
                        first
                        :content)
        result (property-value-for properties "cruise_job_result")]
    (when-not (= "Unknown" result)
      (let [start-time (parse-datetime (property-value-for properties "cruise_timestamp_04_building"))
            end-time (parse-datetime (property-value-for properties "cruise_timestamp_06_completed"))
            actual-stage-run (property-value-for properties "cruise_stage_counter")
            outcome (if (= "Passed" result) "pass" "fail")]
        (assoc (build-times start-time end-time)
               :outcome outcome
               :actual-stage-run actual-stage-run)))))

(defn build-for [go-url job-id]
  (let [build-properties (get-plain go-url (templ/uritemplate "/api/jobs{/id}.xml"
                                                              {"id" job-id}))]
    (parse-build-properties build-properties)))


;; /api/stages/%pipeline/%stage/history

(defn- get-stage-instances [go-url pipeline stage-name offset]
  (let [stage-history (get-json go-url (templ/uritemplate "/api/stages{/pipeline}{/stage}/history{/offset}"
                                                          {"pipeline" pipeline
                                                           "stage" stage-name
                                                           "offset" offset}))
        stage-instances (:stages stage-history)]
    (if (empty? stage-instances)
      []
      (let [next-offset (+ offset (count stage-instances))]
        (concat stage-instances
                (lazy-seq (get-stage-instances go-url pipeline stage-name next-offset)))))))

(defn get-stage-history [go-url pipeline stage]
  (get-stage-instances go-url pipeline stage 0))


;; /api/pipelines/%pipelines/instance/%run

(defn- revision->input [{:keys [modifications material]}]
  {:revision (:revision (first modifications))
   :sourceId (:id material)})

(defn- pipeline-build-cause [{:keys [modifications material changed]}]
  (when (and changed (= "Pipeline" (:type material)))
    (let [revision-tokens (str/split (:revision (first modifications)) #"/")]
      {:pipeline-name (nth revision-tokens 0)
       :pipeline-run (nth revision-tokens 1)
       :stage-name (nth revision-tokens 2)
       :stage-run (nth revision-tokens 3)})))

(defn get-inputs-for-pipeline-run [go-url pipeline-name run]
  (let [pipeline-instance (get-json go-url (templ/uritemplate "/api/pipelines{/pipeline}/instance{/run}"
                                                              {"pipeline" pipeline-name
                                                               "run" run}))
        revisions (:material_revisions (:build_cause pipeline-instance))]
    {:inputs (map revision->input revisions)
     :triggers (keep pipeline-build-cause revisions)}))


;; /api/config/pipeline_groups

(defn- groups->pipelines [{group-name :name pipelines :pipelines}]
  (map #(assoc % :group group-name) pipelines))

(defn get-pipelines [go-url]
  (let [pipeline-groups (get-json go-url "/api/config/pipeline_groups")]
    (mapcat groups->pipelines pipeline-groups)))


;; /files/%pipeline/%run/%stage/%run/%job.json

(defn- looks-like-xml? [file-name]
  (.endsWith file-name "xml"))

(defn- filter-xml-files [file-node]
  (if (contains? file-node :files)
    (mapcat filter-xml-files (:files file-node))
    (if (looks-like-xml? (:name file-node))
      (list (:url file-node))
      [])))

(defn- make-file-url-path-only [url]
  ;; Transform to path-only url so basic auth as provided by the user can be used.
  ;; Also works around broken Go domain setup
  (str/replace url
               #"https?://[^/]+(/.+?)?/files/"
               "/files/"))

(defn- force-evaluate-json [json]
  (doall json))

(defn- try-get-artifact-tree [go-url {:keys [pipeline-name pipeline-run
                                             stage-name stage-run job-name]}]
  (let [artifacts-url (templ/uritemplate "/files{/pipeline}{/run}{/stage}{/stageRun}{/job}.json"
                                         {"pipeline" pipeline-name
                                          "run" pipeline-run
                                          "stage" stage-name
                                          "stageRun" stage-run
                                          "job" job-name})]
    (try
      (force-evaluate-json (get-json go-url artifacts-url))
      (catch JsonParseException e
        (log/errorf e "Unable to parse artifact list for %s" artifacts-url))
      (catch Exception e
        (if-let [data (ex-data e)]
          (log/errorf "Unable to get artifact list from %s (status %s): %s"
                      artifacts-url (:status data) (:body data))
          (log/errorf e "Unable to get artifact list from %s" artifacts-url))))))

(defn- xml-artifacts-for-job-run [go-url job-instance]
  (when-let [file-tree (try-get-artifact-tree go-url job-instance)]
    (map make-file-url-path-only
         (mapcat filter-xml-files file-tree))))

(defn- is-junit-xml? [content]
  (re-find #"^(<\?[^>]+\?>)?(\s+|<!--.*-->)*<testsuite" content))

(defn get-junit-xml [go-url job-instance]
  (when-let [xml-file-urls (seq (xml-artifacts-for-job-run go-url job-instance))]
    (->> xml-file-urls
         (map #(get-plain go-url %))
         (filter is-junit-xml?))))
