(use '[leiningen.exec :only (deps)])
(deps '[[clj-http "1.1.2"]
        [clj-time "0.9.0"]
        [cheshire "5.4.0"]])

(require '[cheshire.core :as j]
         '[clj-http.client :as client]
         '[clj-time.format :as tf]
         '[clj-time.coerce :as tc])

(def server-url (second *command-line-args*))

(defn absolute-url-for [relativeUrl]
  (clojure.string/join [server-url relativeUrl]))

(defn get-json [relative-url-template & url-params]
  (let [relative-url (apply format relative-url-template url-params)
        response (client/get (absolute-url-for relative-url))]
    (j/parse-string (:body response) true)))


;; /jobStatus.json

(defn parse-build-info [json-response]
  (let [buildInfo (:building_info (first json-response))
        buildStartTime (tc/to-long (tf/parse (:build_building_date buildInfo)))
        buildEndTime (tc/to-long (tf/parse (:build_completed_date buildInfo)))
        result (:result buildInfo)
        outcome (if (= "Passed" result) "pass" "fail")]
    {:start buildStartTime
     :end buildEndTime
     :outcome outcome}))

(defn build-for [{jobId :jobId}]
  (let [build (get-json "/jobStatus.json?pipelineName=&stageName=&jobId=%s" jobId)]
    (parse-build-info build)))

(defn job-data-for-instance [jobInstance]
  (assoc jobInstance :build (build-for jobInstance)))


;; /api/stages/%pipeline/%stage/history

(defn job-instances-for-stage-instance [{pipelineRun :pipeline_counter
                                         stageRun :counter
                                         jobs :jobs}]
  (map (fn [{id :id name :name result :result}]
         {:jobId id
          :jobName name
          :stageRun stageRun
          :pipelineRun pipelineRun})
       jobs))

(defn job-instances-for-stage [{stage :stage pipeline :pipeline}]
  (let [stageHistory (get-json "/api/stages/%s/%s/history" pipeline stage)
        stageInstances (:stages stageHistory)]
    (map #(assoc % :stageName stage :pipelineName pipeline)
         (mapcat job-instances-for-stage-instance stageInstances))))


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

(defn stages-for-pipeline-group [pipelineGroupName]
  (let [pipelineGroups (get-json "/api/config/pipeline_groups")
        pipelineGroup (first (filter #(= pipelineGroupName (:name %)) pipelineGroups))
        pipelines (:pipelines pipelineGroup)]
    (mapcat stages-for-pipeline pipelines)))

;; /files/%pipeline/%run/%stage/%run/job.json

(defn looks-like-xml? [file-name]
  (.endsWith file-name "xml"))

(defn filter-xml-files [file-node]
  (if (= "folder" (:type file-node))
    (mapcat filter-xml-files (:files file-node))
    (if (looks-like-xml? (:name file-node))
      (list (:url file-node))
      [])))

(defn xml-artifacts-for-job-run [{pipeline-name :pipelineName
                                  pipeline-run :pipelineRun
                                  stage-name :stageName
                                  stage-run :stageRun
                                  job-name :jobName}]
  (let [artifacts-url (format "/files/%s/%s/%s/%s/%s.json" pipeline-name pipeline-run stage-name stage-run job-name)
        file-tree (get-json artifacts-url)]
    (mapcat filter-xml-files file-tree)))

(defn augment-job-instance-with-junit-xml [job-instance]
  (if-let [xml-file-url (first (xml-artifacts-for-job-run job-instance))]
    (assoc job-instance
           :junit-xml-func
           (fn []
             (println xml-file-url)
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

(defn put-build [job-name build-no build]
  (client/put (format "http://localhost:3000/builds/%s/%s" job-name build-no)
              {:content-type :json
               :body (j/generate-string build)}))

(defn put-junit-xml [job-name build-no xml-content]
  (client/put (format "http://localhost:3000/builds/%s/%s/testresults" job-name build-no)
              {:body xml-content}))

(defn put-to-buildviz [builds]
  (doseq [{jobName :jobName buildNo :buildNo build :build junit-xml-func :junit-xml-func} builds]
    (println jobName buildNo build)
    (put-build jobName buildNo  build)
    (when (some? junit-xml-func)
      (put-junit-xml jobName buildNo (junit-xml-func)))))

(def job-instances
  (->> (concat (stages-for-pipeline-group "Development")
               (stages-for-pipeline-group "Verification")
               (stages-for-pipeline-group "Production"))
       (mapcat job-instances-for-stage)
       (map augment-job-with-inputs)
       (map job-data-for-instance)
       (map augment-job-instance-with-junit-xml)))

(put-to-buildviz (map make-build-instance job-instances))
