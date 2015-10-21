(ns buildviz.go.api
  (:require [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-time
             [coerce :as tc]
             [format :as tf]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(import com.fasterxml.jackson.core.JsonParseException)

(defn- absolute-url-for [go-url relative-url]
  (string/join [go-url relative-url]))

(defn- get-plain [go-url relative-url-template & url-params]
  (let [relative-url (apply format relative-url-template url-params)
        response (client/get (absolute-url-for go-url relative-url))]
    (:body response)))

(defn- get-json [go-url relative-url-template & url-params]
  (j/parse-string (apply get-plain (cons go-url
                                         (cons relative-url-template
                                               url-params)))
                  true))


;; /properties/%pipeline/%pipeline_run/%stage/%stage_run/%job

(defn- handle-missing-start-time-when-cancelled [build-start-time build-end-time]
  (if (nil? build-start-time)
    build-end-time
    build-start-time))

(defn- build-times [start-time end-time]
  (if-not (nil? end-time)
    {:start (handle-missing-start-time-when-cancelled start-time end-time)
     :end end-time}
    {}))

(defn- parse-datetime [property-map key]
  (tc/to-long (tf/parse (get property-map key))))

(defn- parse-build-properties [properties]
  (let [lines (string/split properties #"\n")
        keys (string/split (first lines) #",")
        values (string/split (second lines) #",")
        property-map (zipmap keys values)
        result (get property-map "cruise_job_result")]
    (when-not (= "Unknown" result)
      (let [start-time (parse-datetime property-map "cruise_timestamp_04_building")
            end-time (parse-datetime property-map "cruise_timestamp_06_completed")
            actual-stage-run (get property-map "cruise_stage_counter")
            outcome (if (= "Passed" result) "pass" "fail")]
        (assoc (build-times start-time end-time)
               :outcome outcome
               :actual-stage-run actual-stage-run)))))

(defn build-for [go-url
                 {pipeline-name :pipelineName
                  pipeline-run :pipelineRun
                  stage-name :stageName
                  stage-run :stageRun
                  job-name :jobName}]
  (try
    (let [build-properties (get-plain go-url
                                      "/properties/%s/%s/%s/%s/%s"
                                      pipeline-name pipeline-run stage-name stage-run job-name)]
      (parse-build-properties build-properties))
    (catch Exception e
      ;; Deal with https://github.com/gocd/gocd/issues/1575
      (if-let [data (ex-data e)]
        (log/errorf "Unable to read build information for job %s (%s %s %s %s) (status %s): %s"
                    job-name pipeline-name pipeline-run stage-name stage-run (:status data) (:body data))
        (log/errorf e
                    "Unable to read build information for job %s (%s %s %s %s)"
                    job-name pipeline-name pipeline-run stage-name stage-run)))))


;; /api/stages/%pipeline/%stage/history

(defn- get-stage-instances [go-url pipeline stage-name offset]
  (let [stage-history (get-json go-url "/api/stages/%s/%s/history/%s" pipeline stage-name offset)
        stage-instances (:stages stage-history)]
    (if (empty? stage-instances)
      []
      (let [next-offset (+ offset (count stage-instances))]
        (concat stage-instances
                (lazy-seq (get-stage-instances go-url pipeline stage-name next-offset)))))))

(defn get-stage-history [go-url pipeline stage]
  (get-stage-instances go-url pipeline stage 0))


;; /api/pipelines/%pipelines/instance/%run

(defn- revision->input [{modifications :modifications material :material}]
  (let [{revision :revision} (first modifications)
        source_id (:id material)]
    {:revision revision
     :source_id source_id}))

(defn get-inputs-for-pipeline-run [go-url pipeline-name run]
  (let [pipeline-instance (get-json go-url "/api/pipelines/%s/instance/%s" pipeline-name run)
        revisions (:material_revisions (:build_cause pipeline-instance))]
    (map revision->input revisions)))


;; /api/config/pipeline_groups

(defn- stages-for-pipeline [{pipeline-name :name stages :stages}]
  (->> stages
       (map :name)
       (map #(assoc {}
                    :stage %
                    :pipeline pipeline-name))))

(defn- stages-for-pipeline-group [{group-name :name pipelines :pipelines}]
  (->> pipelines
       (mapcat stages-for-pipeline)
       (map #(assoc % :group group-name))))

(defn get-stages [go-url]
  (let [pipeline-groups (get-json go-url "/api/config/pipeline_groups")]
    (mapcat stages-for-pipeline-group pipeline-groups)))


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
  (string/replace url
                  #"https?://[^/]+(/.+?)?/files/"
                  "/files/"))

(defn- force-evaluate-json [json]
  (doall json))

(defn- try-get-artifact-tree [go-url
                              {pipeline-name :pipelineName
                              pipeline-run :pipelineRun
                              stage-name :stageName
                              stage-run :stageRun
                              job-name :jobName}]
  (let [artifacts-url (format "/files/%s/%s/%s/%s/%s.json" pipeline-name pipeline-run stage-name stage-run job-name)]
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

(defn get-junit-xml [go-url job-instance]
  (when-let [xml-file-url (first (xml-artifacts-for-job-run go-url job-instance))]
    (log/info (format "Reading test results from %s" xml-file-url))
    (get-plain go-url xml-file-url)))
