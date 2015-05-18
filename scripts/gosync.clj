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

(defn jobs-for-stage [pipelineName pipelineNo stage]
  (let [jobs (get stage "jobs")
        stageName (get stage "name")]
    (for
        [job jobs
         :let [jobName (get job "name")
               jobId (get job "id")]]
      {:fullName (format "%s %s %s" pipelineName stageName jobName)
       :run pipelineNo
       :jobId jobId})))

(defn jobs-for-pipeline [pipelineRun]
  (let [stages (get pipelineRun "stages")
        stagesWithJobs (filter #(not (empty? (get % "jobs"))) stages)
        pipelineName (get pipelineRun "name")
        pipelineNo (get pipelineRun "label")]
    (apply concat
           (map (partial jobs-for-stage
                         pipelineName
                         pipelineNo)
                stagesWithJobs))))

(defn history-for [pipeline]
  (let [historyUrl (format "/api/pipelines/%s/history" pipeline)
        historyResp (get-url historyUrl)
        history (j/parse-string (:body historyResp))
        pipelineInfos (get history "pipelines")]
    (apply concat (map jobs-for-pipeline pipelineInfos))))

(defn full-history-for [pipeline]
  (map #(assoc % :build (build-for %))
       (history-for pipeline)))

(defn send-to-go [builds]
  (doseq [{jobName :fullName buildNo :run build :build} builds]
    (println jobName buildNo build)
    (client/put (format "http://localhost:3000/builds/%s/%s" jobName buildNo)
                {:content-type :json
                 :body (j/generate-string build)})))

(send-to-go
 (apply concat
        (map full-history-for (list "Deploy_Consumer" "Deploy_Services" "Deploy_QA" "Deploy_B2B" "B2B_Website" "Web_Services" "Consumer_Website"))))
