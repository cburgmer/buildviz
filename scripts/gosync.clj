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

(defn parse-build-info [jsonResp]
  (let [buildInfo (:building_info (first jsonResp))
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
         (apply concat
                (map job-instances-for-stage-instance stageInstances)))))


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
    (apply concat
           (map stages-for-pipeline pipelines))))


;; upload

(defn make-build-instance [{jobName :jobName stageName :stageName pipelineName :pipelineName
                            stageRun :stageRun pipelineRun :pipelineRun
                            inputs :inputs build :build}]
  {:jobName (format "%s %s %s" pipelineName stageName jobName)
   :buildNo (format "%s %s" pipelineRun stageRun)
   :build (assoc build :inputs inputs)})

(defn put-to-buildviz [builds]
  (doseq [{jobName :jobName buildNo :buildNo build :build} builds]
    (println jobName buildNo build)
    (client/put (format "http://localhost:3000/builds/%s/%s" jobName buildNo)
                {:content-type :json
                 :body (j/generate-string build)})))

(def job-instances
  (->> (concat (stages-for-pipeline-group "Development")
               (stages-for-pipeline-group "Verification")
               (stages-for-pipeline-group "Production"))
       (map job-instances-for-stage)
       (apply concat)
       (map augment-job-with-inputs)
       (map job-data-for-instance)))

(put-to-buildviz (map make-build-instance job-instances))
