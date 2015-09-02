(ns buildviz.gosync
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(import com.fasterxml.jackson.core.JsonParseException)

(def tz (t/default-time-zone))

(def date-formatter (tf/formatter tz "YYYY-MM-dd" "YYYY/MM/dd" "YYYYMMdd" "dd.MM.YYYY"))

(declare go-url)
(declare load-builds-from)
(declare buildviz-url)

(defn usage [options-summary]
  (string/join "\n"
               [""
                "Syncs Go.cd build history with buildviz"
                ""
                "Usage: gosync.clj [OPTIONS] GO_URL [PIPELINE_GROUP] [ANOTHER PIPELINE GROUP] ..."
                ""
                "GO_URL            The URL of the Go.cd installation"
                "PIPELINE_GROUP    Optional name of a pipeline group in Go"
                ""
                "Options"
                options-summary]))

(def cli-options
  [["-b" "--buildviz URL" "URL pointing to a running buildviz instance"
    :id :buildviz-url
    :default "http://localhost:3000"]
   ["-f" "--from DATE" "DATE from which on builds are loaded"
    :id :load-builds-from
    :parse-fn #(tf/parse date-formatter %)
    :default (t/minus (t/today-at-midnight tz) (t/weeks 1))]
   ["-h" "--help"]])

;; util

(defn absolute-url-for [relativeUrl]
  (clojure.string/join [go-url relativeUrl]))

(defn get-json [relative-url-template & url-params]
  (let [relative-url (apply format relative-url-template url-params)
        response (client/get (absolute-url-for relative-url))]
    (j/parse-string (:body response) true)))


;; /jobStatus.json

(defn handle-missing-start-time-when-cancelled [build-start-time build-end-time]
  (if (nil? build-start-time)
    build-end-time
    build-start-time))

(defn build-times [info start-time end-time]
  (if-not (nil? end-time)
    (assoc info
           :start (handle-missing-start-time-when-cancelled start-time end-time)
           :end end-time)
    info))

(defn parse-build-info [json-response]
  (let [build-info (:building_info (first json-response))
        result (:result build-info)]
    (when-not (= "Unknown" result)
      (let [start-time (tc/to-long (tf/parse (:build_building_date build-info)))
            end-time (tc/to-long (tf/parse (:build_completed_date build-info)))
            outcome (if (= "Passed" result) "pass" "fail")]
        (-> {}
            (build-times start-time end-time)
            (assoc :outcome outcome))))))

(defn build-for [{jobId :jobId}]
  (let [build (get-json "/jobStatus.json?pipelineName=&stageName=&jobId=%s" jobId)]
    (parse-build-info build)))

(defn job-data-for-instance [jobInstance]
  (assoc jobInstance :build (build-for jobInstance)))


;; /api/stages/%pipeline/%stage/history

(defn job-instances-for-stage-instance [{pipeline-run :pipeline_counter
                                         stage-run :counter
                                         jobs :jobs}]
  (map (fn [{id :id name :name result :result scheduled-date :scheduled_date}]
         {:jobId id
          :jobName name
          :stageRun stage-run
          :pipelineRun pipeline-run
          :scheduledDateTime (tc/from-long scheduled-date)})
       jobs))

(defn get-stage-instances [pipeline stage offset]
  (let [stage-history (get-json "/api/stages/%s/%s/history/%s" pipeline stage offset)
        stage-instances (:stages stage-history)]
    (if (empty? stage-instances)
      []
      (let [next-offset (+ offset (count stage-instances))]
        (concat stage-instances
                (lazy-seq (get-stage-instances pipeline stage next-offset)))))))

(defn fetch-all-stage-history [pipeline stage]
  (get-stage-instances pipeline stage 0))

(defn job-instances-for-stage [{stage :stage pipeline :pipeline}]
  (map #(assoc % :stageName stage :pipelineName pipeline)
       (take-while #(.isAfter (:scheduledDateTime %) load-builds-from)
                   (mapcat job-instances-for-stage-instance
                           (fetch-all-stage-history pipeline stage)))))


;; /api/pipelines/%pipelines/instance/%run

(defn revision->input [{modifications :modifications material :material}]
  (let [{revision :revision} (first modifications)
        source_id (:id material)]
    {:revision revision
     :source_id source_id}))

(defn inputs-for-pipeline-run [pipelineName run]
  (let [pipelineInstance (get-json "/api/pipelines/%s/instance/%s" pipelineName run)
        revisions (:material_revisions (:build_cause pipelineInstance))]
    (map revision->input revisions)))

(defn augment-job-with-inputs [job]
  (let [pipelineRun (:pipelineRun job)
        pipelineName (:pipelineName job)
        inputs (inputs-for-pipeline-run pipelineName pipelineRun)]
    (assoc job :inputs inputs)))


;; /api/config/pipeline_groups

(defn stages-for-pipeline [pipeline]
  (let [pipelineName (:name pipeline)
        stages (:stages pipeline)]
    (map (fn [{name :name}]
           {:stage name :pipeline pipelineName})
         stages)))

(defn stages-for-pipeline-group [pipeline-group]
  (let [pipelines (:pipelines pipeline-group)]
    (mapcat stages-for-pipeline pipelines)))

(defn get-pipeline-groups []
  (get-json "/api/config/pipeline_groups"))

(defn select-pipeline-groups [pipeline-groups filter-by-names]
  (filter #(contains? filter-by-names (:name %)) pipeline-groups))

;; /files/%pipeline/%run/%stage/%run/%job.json

(defn looks-like-xml? [file-name]
  (.endsWith file-name "xml"))

(defn filter-xml-files [file-node]
  (if (contains? file-node :files)
    (mapcat filter-xml-files (:files file-node))
    (if (looks-like-xml? (:name file-node))
      (list (:url file-node))
      [])))

(defn replace-host-part-for-basic-auth [url]
  ;; Replace host part with go url supplied by user
  ;; Also works around broken Go domain setup
  (clojure.string/replace url #"https?://[^/]+/go" go-url))

(defn try-get-artifact-tree [{pipeline-name :pipelineName
                              pipeline-run :pipelineRun
                              stage-name :stageName
                              stage-run :stageRun
                              job-name :jobName}]
  (let [artifacts-url (format "/files/%s/%s/%s/%s/%s.json" pipeline-name pipeline-run stage-name stage-run job-name)]
    (try
      (doall (get-json artifacts-url))
      (catch JsonParseException e
        (log/errorf e "Unable to parse artifact list for %s" artifacts-url))
      (catch Exception e
        (log/warn (format "Unable to get artifact list from %s, might have been deleted by Go" artifacts-url))
        {}))))

(defn xml-artifacts-for-job-run [job-instance]
  (let [file-tree (try-get-artifact-tree job-instance)]
    (map replace-host-part-for-basic-auth
         (mapcat filter-xml-files file-tree))))

(defn augment-job-instance-with-junit-xml [job-instance]
  (if-let [xml-file-url (first (xml-artifacts-for-job-run job-instance))]
    (assoc job-instance
           :junit-xml-func
           (fn []
             (log/info (format "Reading test results from %s" xml-file-url))
             (:body (client/get xml-file-url))))
    job-instance))

;; upload

(defn make-build-instance [{jobName :jobName stageName :stageName pipelineName :pipelineName
                            stageRun :stageRun pipelineRun :pipelineRun
                            junit-xml-func :junit-xml-func
                            inputs :inputs build :build}]
  {:jobName (format "%s %s %s" pipelineName stageName jobName)
   :buildNo (format "%s %s" pipelineRun stageRun)
   :junit-xml-func junit-xml-func
   :build (assoc build :inputs inputs)})

(defn buildviz-build-base-url [job-name build-no]
  (format "%s/builds/%s/%s" buildviz-url job-name build-no))

(defn put-build [job-name build-no build]
  (client/put (buildviz-build-base-url job-name build-no)
              {:content-type :json
               :body (j/generate-string build)}))

(defn put-junit-xml [job-name build-no xml-content]
  (client/put (clojure.string/join [(buildviz-build-base-url job-name build-no) "/testresults"])
              {:body xml-content}))

(defn dot []
  (print ".")
  (flush))

(defn put-to-buildviz [builds]
  (doseq [{job-name :jobName build-no :buildNo build :build junit-xml-func :junit-xml-func} builds]
    (dot)
    (log/info (format "Syncing %s %s: build" job-name build-no build))
    (put-build job-name build-no  build)
    (when (some? junit-xml-func)
      (try
        (put-junit-xml job-name build-no (junit-xml-func))
        (catch Exception e
          (if-let [data (ex-data e)]
            (log/errorf "Unable to sync testresults for %s %s (status %s): %s" job-name build-no (:status data) (:body data))
            (log/errorf e "Unable to sync testresults for %s %s" job-name build-no)))))))

;; run

(defn -main [& c-args]
  (def args (parse-opts c-args cli-options))

  (when (or (:help (:options args))
            (empty? (:arguments args)))
    (println (usage (:summary args)))
    (System/exit 0))

  (def go-url (first (:arguments args)))
  (def buildviz-url (:buildviz-url (:options args)))

  (def load-builds-from (:load-builds-from (:options args)))

  (def selected-pipeline-group-names (set (drop 1 (:arguments args))))

  (println "Go" selected-pipeline-group-names go-url "-> buildviz" buildviz-url)
  (println "Syncing all builds starting from" (tf/unparse date-formatter load-builds-from))

  (def pipeline-groups (get-pipeline-groups))

  (def selected-pipeline-groups
    (if (seq selected-pipeline-group-names)
      (select-pipeline-groups pipeline-groups selected-pipeline-group-names)
      pipeline-groups))

  (def job-instances
    (->> selected-pipeline-groups
         (mapcat stages-for-pipeline-group)
         (mapcat job-instances-for-stage)
         (map augment-job-with-inputs)
         (map job-data-for-instance)
         (filter #(some? (:build %)))
         (map augment-job-instance-with-junit-xml)))

  (put-to-buildviz (map make-build-instance job-instances))

  (println)
  (println (format "Done, wrote %s build entries" (count job-instances))))
