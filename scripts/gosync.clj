(use '[leiningen.exec :only (deps)])
(deps '[[clj-http "1.1.2"]
        [clj-time "0.9.0"]
        [cheshire "5.4.0"]])

(require '[cheshire.core :as j]
         '[clj-http.client :as client]
         '[clj-time.format :as tf]
         '[clj-time.coerce :as tc])

(def server-url (second *command-line-args*))

(defn absolute-url [relativeUrl]
  (clojure.string/join [server-url relativeUrl]))

(defn get-url [relativeUrl]
  (client/get (absolute-url relativeUrl)))

; /jobStatus.json

(defn parse-build-info [jsonResp]
  (let [buildInfo (get (first jsonResp) "building_info")
        buildStartTime (tc/to-long (tf/parse (get buildInfo "build_building_date")))
        buildEndTime (tc/to-long (tf/parse (get buildInfo "build_completed_date")))
        result (get buildInfo "result")
        outcome (if (= "Passed" result) "pass" "fail")]
    {:start buildStartTime
     :end buildEndTime
     :outcome outcome}))

(defn build-for [{jobId :jobId}]
  (let [buildUrl (format "/jobStatus.json?pipelineName=&stageName=&jobId=%s" jobId)
        buildResp (get-url buildUrl)
        build (j/parse-string (:body buildResp))]
    (parse-build-info build)))

(defn job-data-for-instance [jobInstance]
  (assoc jobInstance :build (build-for jobInstance)))


; /api/stages/%pipeline/%stage/history

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
  (let [stageHistoryUrl (format "/api/stages/%s/%s/history" pipeline stage)
        stageHistoryResp (get-url stageHistoryUrl)
        stageHistory (j/parse-string (:body stageHistoryResp) true)
        stageInstances (:stages stageHistory)]
    (map #(assoc % :stageName stage :pipelineName pipeline)
         (apply concat
                (map #(job-instances-for-stage-instance %) stageInstances)))))


; /api/config/pipeline_groups

(defn stages-for-pipeline [pipeline]
  (let [pipelineName (:name pipeline)
        stages (:stages pipeline)]
    (map (fn [{name :name}]
           {:stage name :pipeline pipelineName})
         stages)))

(defn stages-for-pipeline-group [pipelineGroupName]
    (let [pipelineGroupsUrl "/api/config/pipeline_groups"
          pipelineGroupsResp (get-url pipelineGroupsUrl)
          pipelineGroups (j/parse-string (:body pipelineGroupsResp) true)
          pipelineGroup (first (filter #(= pipelineGroupName (:name %)) pipelineGroups))
          pipelines (:pipelines pipelineGroup)]
      (apply concat
             (map stages-for-pipeline pipelines))))

; upload

(defn make-build-instance [{jobName :jobName stageName :stageName pipelineName :pipelineName
                            stageRun :stageRun pipelineRun :pipelineRun
                            build :build}]
  {:jobName (format "%s %s %s" pipelineName stageName jobName)
   :buildNo (format "%s %s" pipelineRun stageRun)
   :build build})

(defn put-to-buildviz [builds]
  (doseq [{jobName :jobName buildNo :buildNo build :build} builds]
    (println jobName buildNo build)
    (client/put (format "http://localhost:3000/builds/%s/%s" jobName buildNo)
                {:content-type :json
                 :body (j/generate-string build)})))

(def job-instances
  (map job-data-for-instance
       (apply concat
              (map job-instances-for-stage
                   (stages-for-pipeline-group "Development")))))

(put-to-buildviz (map make-build-instance job-instances))
