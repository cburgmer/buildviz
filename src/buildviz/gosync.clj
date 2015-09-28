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
    :id :sync-start-time
    :parse-fn #(tf/parse date-formatter %)]
   ["-j" "--sync-jobs PIPELINE" "PIPELINE for which Go jobs will be synced individually (not aggregated inside stage)"
    :id :sync-jobs
    :default '()
    :assoc-fn (fn [previous key val] (assoc previous key (conj (get previous key) val)))]
   ["-h" "--help"]])


(defn- testsuite? [elem]
  (= :testsuite (:tag elem)))

(defn testsuite-list [junit-xml]
  (let [root (xml/parse-str junit-xml)]
    (if (testsuite? root)
      (list root)
      (:content root))))

(defn aggregate-junit-xml-testsuites [junit-xml-list]
  (xml/emit-str (apply xml/element (cons :testsuites
                                         (cons {}
                                               (mapcat testsuite-list junit-xml-list))))))

(defn aggregate-junit-xml [{pipeline-name :pipelineName
                            pipeline-run :pipelineRun
                            stage-name :stageName
                            stage-run :stageRun
                            job-instances :job-instances}]
  (let [all-junit-xml (map :junit-xml job-instances)
        junit-xml-list (remove nil? all-junit-xml)]
    (when-not (empty? junit-xml-list)
      (when-not (= (count junit-xml-list) (count all-junit-xml))
        (log/infof "Unable to accumulate all JUnit XML for jobs of %s %s (%s %s)"
                   pipeline-name stage-name pipeline-run stage-run))
      (aggregate-junit-xml-testsuites junit-xml-list))))


(defn aggregate-build-times [job-instances]
  (let [start-times (map :start job-instances)
        end-times (map :end job-instances)]
    (if (empty? (filter nil? end-times))
      {:start (apply min start-times)
       :end (apply max end-times)}
      {})))

(defn aggregate-builds [job-instances]
  (let [outcomes (map :outcome job-instances)
        accumulated-outcome (if (every? #(= "pass" %) outcomes)
                              "pass"
                              "fail")]
    (assoc (aggregate-build-times job-instances)
           :outcome accumulated-outcome)))


(defn ignore-old-runs-for-rerun-stages [job-instances stage-run]
  (filter #(= stage-run (:actual-stage-run %)) job-instances))

(defn- aggregate-build [{stage-run :stageRun
                         stage-name :stageName
                         job-instances :job-instances}]
  (-> job-instances
      (ignore-old-runs-for-rerun-stages stage-run)
      aggregate-builds
      (assoc :name stage-name)))

(defn- aggregate-jobs-for-stage [stage-instance]
  (let [aggregated-junit-xml (aggregate-junit-xml stage-instance)
        aggregated-build (aggregate-build stage-instance)]
    (assoc stage-instance
           :job-instances (list (assoc aggregated-build
                                       :junit-xml aggregated-junit-xml)))))

(defn- aggregate-jobs-for-stage-instance [stage-instance sync-jobs-for-pipelines]
  (let [{pipeline-name :pipelineName} stage-instance]
    (if (contains? sync-jobs-for-pipelines pipeline-name)
      stage-instance
      (aggregate-jobs-for-stage stage-instance))))


(defn- build-for-job [stage-instance job-name]
  (let [job-instance (assoc stage-instance :jobName job-name)]
    (-> (goapi/build-for go-url job-instance)
        (assoc :name job-name)
        (assoc :junit-xml (goapi/get-junit-xml go-url job-instance)))))

(defn- add-job-instances-for-stage-instance [stage-instance]
  (let [job-names (:job-names stage-instance)]
    (assoc stage-instance
           :job-instances (map #(build-for-job stage-instance %) job-names))))


(defn parse-stage-instance [{pipeline-run :pipeline_counter
                             stage-run :counter
                             jobs :jobs}]
  {:stageRun stage-run
   :pipelineRun pipeline-run
   :scheduled-time (tc/from-long (apply min (map :scheduled_date jobs)))
   :job-names (map :name jobs)})

(defn stage-instances-from [sync-start-time {stage-name :stage pipeline-name :pipeline}]
  (let [safe-build-start-date (t/minus sync-start-time (t/millis 1))]
    (->> (goapi/get-stage-history go-url pipeline-name stage-name)
         (map parse-stage-instance)
         (map #(assoc % :stageName stage-name :pipelineName pipeline-name))
         (take-while #(t/after? (:scheduled-time %) safe-build-start-date)))))


(defn add-inputs-for-stage-instance [stage-instance]
  (let [pipeline-run (:pipelineRun stage-instance)
        pipeline-name (:pipelineName stage-instance)
        inputs (goapi/get-inputs-for-pipeline-run go-url pipeline-name pipeline-run)]
    (assoc stage-instance :inputs inputs)))


(defn select-stages [filter-groups stages]
  (if (seq filter-groups)
    (filter #(contains? filter-groups (:group %)) stages)
    stages))


;; upload

(defn- job-name [pipeline-name stage-name job-name]
  (if (= stage-name job-name)
    (format "%s %s" pipeline-name stage-name)
    (format "%s %s %s" pipeline-name stage-name job-name)))

(defn- stage-instances->builds [{pipeline-name :pipelineName
                                 pipeline-run :pipelineRun
                                 stage-name :stageName
                                 stage-run :stageRun
                                 inputs :inputs
                                 job-instances :job-instances}]
  (map (fn [{outcome :outcome
             start :start
             end :end
             name :name
             junit-xml :junit-xml}]
         {:job-name (job-name pipeline-name stage-name name)
          :build-id (format "%s %s" pipeline-run stage-run)
          :junit-xml junit-xml
          :build {:start start
                  :end end
                  :outcome outcome
                  :inputs inputs}})
       job-instances))

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

(defn put-to-buildviz [{job-name :job-name build-no :build-id build :build junit-xml :junit-xml}]
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
          (log/errorf e "Unable to sync testresults for %s %s" job-name build-no))))))

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

(defn- emit-start [sync-start-time pipeline-stages]
  (println "Looking at pipeline groups" (distinct (map :group pipeline-stages)))
  (println "Syncing all stages starting from" (tf/unparse (:date-time tf/formatters) sync-start-time))

  (println "Finding all pipeline runs for syncing...")

  pipeline-stages)

(defn- emit-count-items [builds]
  (println (format "Found %s stage instances to be synced, starting" (count builds)))
  builds)

(defn- emit-end [builds]
  (let [build-count (count builds)]
    (println)
    (println (format "Done, wrote %s build entries" build-count))))

(defn -main [& c-args]
  (def args (parse-opts c-args cli-options))

  (when (or (:help (:options args))
            (empty? (:arguments args)))
    (println (usage (:summary args)))
    (System/exit 0))

  (def go-url (first (:arguments args)))
  (def buildviz-url (:buildviz-url (:options args)))

  (println "Go" go-url "-> buildviz" buildviz-url)

  (let [sync-start-time (get-start-date (:sync-start-time (:options args)))
        sync-jobs-for-pipelines (set (:sync-jobs (:options args)))
        selected-pipeline-group-names (set (drop 1 (:arguments args)))]

    (->> (goapi/get-stages go-url)
         (select-stages selected-pipeline-group-names)
         (emit-start sync-start-time)
         (mapcat #(stage-instances-from sync-start-time %))
         (sort-by :scheduled-time)
         emit-count-items
         (map add-inputs-for-stage-instance)
         (map add-job-instances-for-stage-instance)
         (map #(aggregate-jobs-for-stage-instance % sync-jobs-for-pipelines))
         (mapcat stage-instances->builds)
         (map put-to-buildviz)
         emit-end)))
