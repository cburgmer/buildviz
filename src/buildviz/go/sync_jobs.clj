(ns buildviz.go.sync-jobs
  (:require [buildviz.go.aggregate :as goaggregate]
            [buildviz.go.api :as goapi]
            [buildviz.go.junit :as junit]
            [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-progress.core :as progress]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [uritemplate-clj.core :as templ])
  (:import [javax.xml.stream.XMLStreamException]))

(defn- aggregate-jobs-for-stage-instance [stage-instance sync-jobs-for-pipelines]
  (let [{pipeline-name :pipeline-name} stage-instance]
    (if (contains? sync-jobs-for-pipelines pipeline-name)
      stage-instance
      (goaggregate/aggregate-jobs-for-stage stage-instance))))


(defn- build-for-job [go-url stage-instance {:keys [name id]}]
  (let [job-instance (assoc stage-instance :job-name name)]
    (-> (goapi/build-for go-url id)
        (assoc :name name)
        (assoc :junit-xml (goapi/get-junit-xml go-url job-instance)))))

(defn- add-job-instances-for-stage-instance [go-url stage-instance]
  (let [jobs (:jobs stage-instance)]
    (assoc stage-instance
           :job-instances (map #(build-for-job go-url stage-instance %) jobs))))


(defn- parse-stage-instance [{pipeline-run :pipeline_counter
                              stage-run :counter
                              result :result
                              jobs :jobs}]
  {:stage-run stage-run
   :pipeline-run pipeline-run
   :finished (not= "Unknown" result)
   :scheduled-time (tc/from-long (apply min (map :scheduled_date jobs)))
   :jobs jobs})

(defn- stage-instances-from [go-url sync-start-time {stage-name :stage pipeline-name :pipeline}]
  (let [safe-build-start-date (t/minus sync-start-time (t/millis 1))]
    (->> (goapi/get-stage-history go-url pipeline-name stage-name)
         (map parse-stage-instance)
         (map #(assoc % :stage-name stage-name :pipeline-name pipeline-name))
         (take-while #(t/after? (:scheduled-time %) safe-build-start-date)))))


(defn- add-inputs-for-stage-instance [go-url {:keys [pipeline-run, pipeline-name] :as stage-instance}]
  (let [inputs (goapi/get-inputs-for-pipeline-run go-url pipeline-name pipeline-run)]
    (assoc stage-instance :inputs inputs)))


(defn- select-stages [filter-groups stages]
  (if (seq filter-groups)
    (filter #(contains? filter-groups (:group %)) stages)
    stages))


;; upload

(defn- job-name [pipeline-name stage-name job-name]
  (if (= stage-name job-name)
    (format "%s :: %s" pipeline-name stage-name)
    (format "%s :: %s :: %s" pipeline-name stage-name job-name)))

(defn- stage-instances->builds [{:keys [pipeline-name pipeline-run stage-name stage-run inputs job-instances]}]
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

(defn- put-build [buildviz-url job-name build-no build]
  (client/put (str/join [(url/with-plain-text-password buildviz-url) (templ/uritemplate "/builds{/job}{/build}" {"job" job-name "build" build-no})])
              {:content-type :json
               :body (j/generate-string build)}))

(defn- put-junit-xml [buildviz-url job-name build-no junit-xml]
  (let [xml-content (junit/merge-junit-xml junit-xml)]
    (client/put (str/join [(url/with-plain-text-password buildviz-url) (templ/uritemplate "/builds{/job}{/build}/testresults" {"job" job-name "build" build-no})])
                {:body xml-content})))

(defn- put-to-buildviz [buildviz-url {job-name :job-name build-no :build-id build :build junit-xml :junit-xml}]
  (log/info (format "Syncing %s %s: build" job-name build-no))
  (put-build buildviz-url job-name build-no build)
  (when (some? junit-xml)
    (try
      (put-junit-xml buildviz-url job-name build-no junit-xml)
      (catch javax.xml.stream.XMLStreamException e
        (do
          (log/errorf e "Unable parse JUnit XML from artifacts for %s %s." job-name build-no)
          (log/info "Offending XML content is:\n" junit-xml)))
      (catch Exception e
        (if-let [data (ex-data e)]
          (do
            (log/errorf "Unable to sync testresults for %s %s (status %s): %s" job-name build-no (:status data) (:body data))
            (log/info "Offending XML content is:\n" junit-xml))
          (log/errorf e "Unable to sync testresults for %s %s" job-name build-no))))))

;; run

(defn- emit-start [go-url buildviz-url sync-start-time pipeline-stages]
  (println "Go" (str go-url) (distinct (map :group pipeline-stages)) "-> buildviz" (str buildviz-url))
  (print (format "Finding all pipeline runs for syncing (starting from %s)..."
                 (tf/unparse (:date-time tf/formatters) sync-start-time)))
  (flush)

  pipeline-stages)

(defn- emit-sync-start [pipeline-stages]
  (println "done")
  pipeline-stages)

(defn sync-stages [go-url buildviz-url sync-start-time sync-jobs-for-pipelines selected-pipeline-group-names]
  (->> (goapi/get-stages go-url)
       (select-stages selected-pipeline-group-names)
       (emit-start go-url buildviz-url sync-start-time)
       (mapcat #(stage-instances-from go-url sync-start-time %))
       (sort-by :scheduled-time)
       (take-while :finished)
       (emit-sync-start)
       (progress/init "Syncing")
       (map #(add-inputs-for-stage-instance go-url %))
       (map #(add-job-instances-for-stage-instance go-url %))
       (map #(aggregate-jobs-for-stage-instance % sync-jobs-for-pipelines))
       (mapcat stage-instances->builds)
       (map #(put-to-buildviz buildviz-url %))
       (map progress/tick)
       dorun
       (progress/done)))
