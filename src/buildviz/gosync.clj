(ns buildviz.gosync
  (:require [buildviz.go-api :as goapi]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]
            [clojure.data.xml :as xml]
            [clojure.string :as string]
            [clojure.tools
             [cli :refer [parse-opts]]
             [logging :as log]])
  (:gen-class))

(def tz (t/default-time-zone))

(def date-formatter (tf/formatter tz "YYYY-MM-dd" "YYYY/MM/dd" "YYYYMMdd" "dd.MM.YYYY"))

(declare go-url)
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
   ["-f" "--from DATE" "DATE from which on builds are loaded (by default tries to pick up where the last run finished)"
    :id :load-builds-from
    :parse-fn #(tf/parse date-formatter %)]
   ["-s" "--aggregate PIPELINE" "PIPELINE for which a complete stage will be synced as a job"
    :id :aggregate-jobs-for-pipelines
    :default '()
    :assoc-fn (fn [previous key val] (assoc previous key (conj (get previous key) val)))]
   ["-h" "--help"]])

;; util

(defn accumulate-build-times [builds]
  (let [start-times (map :start builds)
        end-times (map :end builds)]
    (if (empty? (filter nil? end-times))
      {:start (apply min start-times)
       :end (apply max end-times)}
      {})))

(defn accumulate-builds [builds]
  (let [outcomes (map :outcome builds)
        accumulated-outcome (if (every? #(= "pass" %) outcomes)
                              "pass"
                              "fail")]
    (assoc (accumulate-build-times builds)
           :outcome accumulated-outcome)))

(defn ignore-old-runs-for-rerun-stages [{stage-run :stageRun} builds]
  (filter #(= stage-run (:actual-stage-run %)) builds))

(defn build-for-job [job-instance]
  (if-let [jobs-for-accumulation (:jobs-for-accumulation job-instance)]
    (->> jobs-for-accumulation
         (map #(merge job-instance %))
         (map (partial goapi/build-for go-url))
         (ignore-old-runs-for-rerun-stages job-instance)
         accumulate-builds)
    (dissoc (goapi/build-for go-url job-instance)
            :actual-stage-run)))

(defn job-data-for-instance [jobInstance]
  (assoc jobInstance :build (build-for-job jobInstance)))


(defn job-instances-for-stage-instance [{pipeline-run :pipeline_counter
                                         stage-run :counter
                                         jobs :jobs}]
  (map (fn [{id :id name :name scheduled-date :scheduled_date}]
         {:jobId id
          :jobName name
          :stageRun stage-run
          :pipelineRun pipeline-run
          :scheduledDateTime (tc/from-long scheduled-date)})
       jobs))

(defn job-instance-for-accumulated-stage-instance [{pipeline-run :pipeline_counter
                                                    stage-run :counter
                                                    jobs :jobs}]
  (let [job-instances (map (fn [{id :id name :name}]
                             {:jobId id :jobName name})
                           jobs)]
    {:jobId 0
     :jobName "accumulated"
     :stageRun stage-run
     :pipelineRun pipeline-run
     :scheduledDateTime (tc/from-long (apply min (map :scheduled_date jobs)))
     :jobs-for-accumulation job-instances}))

(defn fetch-job-instances-for-stage-instance [accumulate-stages-for-pipelines stage-name stage-instance]
  (if (contains? accumulate-stages-for-pipelines stage-name)
    (list (job-instance-for-accumulated-stage-instance stage-instance))
    (job-instances-for-stage-instance stage-instance)))

(defn job-instances-for-stage [load-builds-from accumulate-stages-for-pipelines {stage-name :stage pipeline :pipeline}]
  (let [safe-build-start-date (t/minus load-builds-from (t/millis 1))]
    (->> stage-name
         (goapi/get-stage-history go-url pipeline)
         (mapcat (partial fetch-job-instances-for-stage-instance accumulate-stages-for-pipelines pipeline))
         (take-while #(t/after? (:scheduledDateTime %) safe-build-start-date))
         (map #(assoc % :stageName stage-name :pipelineName pipeline)))))


(defn augment-job-with-inputs [job]
  (let [pipeline-run (:pipelineRun job)
        pipeline-name (:pipelineName job)
        inputs (goapi/get-inputs-for-pipeline-run go-url pipeline-name pipeline-run)]
    (assoc job :inputs inputs)))


(defn select-stages [stages filter-groups]
  (if (seq filter-groups)
    (filter #(contains? filter-groups (:group %)) stages)
    stages))


(defn- testsuite? [elem]
  (= :testsuite (:tag elem)))

(defn testsuite-list [junit-xml]
  (let [root (xml/parse-str junit-xml)]
    (if (testsuite? root)
      (list root)
      (:content root))))

(defn accumulate-junit-xml-results [junit-xml-list]
  (xml/emit-str (apply xml/element (cons :testsuites
                                         (cons {}
                                               (mapcat testsuite-list junit-xml-list))))))

(defn get-all-junit-xml [job-instance]
  (let [jobs (:jobs-for-accumulation job-instance)]
    (->> jobs
         (map #(merge job-instance %))
         (map (partial goapi/get-junit-xml go-url)))))

(defn get-accumulated-junit-xml [job-instance]
  (let [all-junit-xml (get-all-junit-xml job-instance)
        junit-xml-list (remove nil? all-junit-xml)]
    (when-not (empty? junit-xml-list)
      (when-not (= (count junit-xml-list) (count all-junit-xml))
        (let [{pipeline-name :pipelineName
               pipeline-run :pipelineRun
               stage-name :stageName
               stage-run :stageRun} job-instance]
          (log/warnf "Unable to accumulate all JUnit XML for jobs of %s %s (%s %s)"
                   pipeline-name stage-name pipeline-run stage-run)))
      (accumulate-junit-xml-results junit-xml-list))))

(defn fetch-junit-xml [job-instance]
  (if (contains? job-instance :jobs-for-accumulation)
    (get-accumulated-junit-xml job-instance)
    (goapi/get-junit-xml go-url job-instance)))

(defn augment-job-instance-with-junit-xml [job-instance]
  (if-let [junit-xml (fetch-junit-xml job-instance)]
    (assoc job-instance
           :junit-xml junit-xml)
    job-instance))

;; upload

(defn make-build-instance [{jobName :jobName stageName :stageName pipelineName :pipelineName
                            stageRun :stageRun pipelineRun :pipelineRun
                            junit-xml :junit-xml
                            inputs :inputs build :build}]
  {:jobName (format "%s %s %s" pipelineName stageName jobName)
   :buildNo (format "%s %s" pipelineRun stageRun)
   :junit-xml junit-xml
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
  (doseq [{job-name :jobName build-no :buildNo build :build junit-xml :junit-xml} builds]
    (dot)
    (log/info (format "Syncing %s %s: build" job-name build-no build))
    (put-build job-name build-no  build)
    (when (some? junit-xml)
      (try
        (put-junit-xml job-name build-no junit-xml)
        (catch Exception e
          (if-let [data (ex-data e)]
            (do
              (log/errorf "Unable to sync testresults for %s %s (status %s): %s" job-name build-no (:status data) (:body data))
              (log/info "Offending XML content is:\n" junit-xml))
            (log/errorf e "Unable to sync testresults for %s %s" job-name build-no)))))))

;; build load start date

(def last-week (t/minus (t/today-at-midnight tz) (t/weeks 1)))

(defn- get-latest-synced-build-start []
  (let [response (client/get (format "%s/status" buildviz-url))
        buildviz-status (j/parse-string (:body response) true)]
    (when-let [latest-build-start (:latestBuildStart buildviz-status)]
      (tc/from-long latest-build-start))))

(defn- get-start-date [date-from-config]
  (if (some? date-from-config)
    date-from-config
    (if-let [latest-sync-build-start (get-latest-synced-build-start)]
      latest-sync-build-start
      last-week)))

;; run

(defn -main [& c-args]
  (def args (parse-opts c-args cli-options))

  (when (or (:help (:options args))
            (empty? (:arguments args)))
    (println (usage (:summary args)))
    (System/exit 0))

  (def go-url (first (:arguments args)))
  (def buildviz-url (:buildviz-url (:options args)))

  (println "Go" go-url "-> buildviz" buildviz-url)

  (let [load-builds-from (get-start-date (:load-builds-from (:options args)))
        accumulate-stages-for-pipelines (set (:aggregate-jobs-for-pipelines (:options args)))
        selected-pipeline-group-names (set (drop 1 (:arguments args)))
        pipeline-stages (goapi/get-stages go-url)
        selected-pipeline-stages (select-stages pipeline-stages selected-pipeline-group-names)]
    (println "Looking at pipeline groups" (distinct (map :group selected-pipeline-stages)))
    (println "Syncing all builds starting from" (tf/unparse (:date-time tf/formatters) load-builds-from))
    (when (some? accumulate-stages-for-pipelines)
      (println "Aggregating jobs for stages of" accumulate-stages-for-pipelines))

    (println "Finding all builds for syncing...")

    (let [builds-to-be-synced (->> selected-pipeline-stages
                                 (mapcat (partial job-instances-for-stage load-builds-from accumulate-stages-for-pipelines))
                                 (sort-by :scheduledDateTime))]
    (println (format "Found %s builds to be synced, starting" (count builds-to-be-synced)))

    (let [builds-with-full-information (->> builds-to-be-synced
                                            (map augment-job-with-inputs)
                                            (map job-data-for-instance)
                                            (filter #(some? (:build %)))
                                            (map augment-job-instance-with-junit-xml))]
      (put-to-buildviz (map make-build-instance builds-with-full-information))

      (println)
      (println (format "Done, wrote %s build entries" (count builds-with-full-information)))))))
