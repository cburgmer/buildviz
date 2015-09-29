(ns buildviz.go.sync
  (:require [buildviz.go
             [aggregate :as goaggregate]
             [api :as goapi]]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-progress.core :as progress]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]
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
                "Usage: gosync.clj [OPTIONS] GO_URL"
                ""
                "GO_URL            The URL of the Go.cd installation"
                ""
                "Options"
                options-summary]))

(def cli-options
  [["-b" "--buildviz URL" "URL pointing to a running buildviz instance"
    :id :buildviz-url
    :default "http://localhost:3000"]
   ["-f" "--from DATE" "Date from which on builds are loaded, if not specified tries to pick up where the last run finished"
    :id :sync-start-time
    :parse-fn #(tf/parse date-formatter %)]
   ["-j" "--sync-jobs PIPELINE" "Pipelines for which Go jobs will be synced individually and not aggregated inside stage"
    :id :sync-jobs-for-pipelines
    :default nil
    :assoc-fn (fn [previous key val] (assoc previous key (conj (get previous key) val)))]
   ["-g" "--pipeline-group PIPELINE_GROUP" "Go pipeline groups to be synced, all by default"
    :id :pipeline-groups
    :default nil
    :assoc-fn (fn [previous key val] (assoc previous key (conj (get previous key) val)))]
   ["-h" "--help"]])


(defn- aggregate-jobs-for-stage-instance [stage-instance sync-jobs-for-pipelines]
  (let [{pipeline-name :pipelineName} stage-instance]
    (if (contains? sync-jobs-for-pipelines pipeline-name)
      stage-instance
      (goaggregate/aggregate-jobs-for-stage stage-instance))))


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

(defn put-to-buildviz [{job-name :job-name build-no :build-id build :build junit-xml :junit-xml}]
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
  (println "Go" go-url (distinct (map :group pipeline-stages)) "-> buildviz" buildviz-url)
  (print (format "Finding all pipeline runs for syncing (starting from %s)..."
                 (tf/unparse (:date-time tf/formatters) sync-start-time)))

  pipeline-stages)

(defn- emit-sync-start [pipeline-stages]
  (println "done")
  pipeline-stages)

(defn -main [& c-args]
  (def args (parse-opts c-args cli-options))

  (when (or (:help (:options args))
            (empty? (:arguments args)))
    (println (usage (:summary args)))
    (System/exit 0))

  (def go-url (first (:arguments args)))
  (def buildviz-url (:buildviz-url (:options args)))

  (let [sync-start-time (get-start-date (:sync-start-time (:options args)))
        sync-jobs-for-pipelines (set (:sync-jobs-for-pipelines (:options args)))
        selected-pipeline-group-names (set (:pipeline-groups (:options args)))]

    (->> (goapi/get-stages go-url)
         (select-stages selected-pipeline-group-names)
         (emit-start sync-start-time)
         (mapcat #(stage-instances-from sync-start-time %))
         (sort-by :scheduled-time)
         (emit-sync-start)
         (progress/init "Syncing")
         (map add-inputs-for-stage-instance)
         (map add-job-instances-for-stage-instance)
         (map #(aggregate-jobs-for-stage-instance % sync-jobs-for-pipelines))
         (mapcat stage-instances->builds)
         (map put-to-buildviz)
         (map progress/tick)
         dorun
         (progress/done))))
