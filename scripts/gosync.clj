(use '[leiningen.exec :only (deps)])
(deps '[[clj-http "1.1.2"]
        [clj-time "0.9.0"]
        [cheshire "5.4.0"]])

(require '[cheshire.core :as j]
         '[clj-http.client :as client]
         '[clj-time.format :as tf]
         '[clj-time.coerce :as tc])

(def server-url (second *command-line-args*))

(defn build-for [pipeline {stage :name id :id}]
  (let [buildUrl (format "%s/jobStatus.json?pipelineName=%s&stageName=%s&jobId=%s" server-url pipeline stage id)
        buildResp (client/get buildUrl)
        build (j/parse-string (:body buildResp))
        buildInfo (get (first build) "building_info")
        buildStartTime (tc/to-long (tf/parse (get buildInfo "build_building_date")))
        buildEndTime (tc/to-long (tf/parse (get buildInfo "build_completed_date")))
        result (get buildInfo "result")
        outcome (if (= "Passed" result) "pass" "fail")]
    {:start buildStartTime
     :end buildEndTime
     :outcome outcome}))

(defn build-info [jobs]
  (let [job (first jobs)] ; TODO multi job stages?
    {:name (get job "name")
     :id (get job "id")}))

(defn build-for-stage [pipelineName pipelineNo stage]
  (let [build (build-info (get stage "jobs"))
        stageName (get stage "name")
        buildName (format "%s_%s" pipelineName stageName)]
    [buildName pipelineNo build]))

(defn stages-for-pipeline [pipelineRun]
  (let [stages (get pipelineRun "stages")
        stagesWithJobs (filter #(not (empty? (get % "jobs"))) stages)
        pipelineName (get pipelineRun "name")
        pipelineNo (get pipelineRun "label")]
    (map (partial build-for-stage pipelineName pipelineNo) stagesWithJobs)))

(defn history-for [pipeline]
  (let [historyUrl (format "%s/api/pipelines/%s/history" server-url pipeline)
        historyResp (client/get historyUrl)
        history (j/parse-string (:body historyResp))
        pipelineInfos (get history "pipelines")]
    (apply concat (map stages-for-pipeline pipelineInfos))))

(defn full-history-for [pipeline]
  (map (fn [[buildName buildNo build]] [buildName buildNo (build-for pipeline build)])
       (history-for pipeline)))

(defn send-to-go [builds]
  (doseq [[jobName buildNo build] builds]
    (println jobName buildNo build)
    (client/put (format "http://localhost:3000/builds/%s/%s" jobName buildNo)
                {:content-type :json
                 :body (j/generate-string build)})
    ))

(send-to-go
 (apply concat
        (map full-history-for (list "Deploy_Consumer" "Deploy_Services" "Deploy_QA" "Deploy_B2B" "B2B_Website" "Web_Services" "Consumer_Website"))))
