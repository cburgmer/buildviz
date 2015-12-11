(ns buildviz.controllers.jobs
  (:require [buildviz
             [csv :as csv]
             [http :as http]
             [jobinfo :as jobinfo]]
            [buildviz.data.results :as results]))

(defn- average-runtime-for [build-data-entries]
  (if-let [avg-runtime (jobinfo/average-runtime build-data-entries)]
    {:averageRuntime avg-runtime}))

(defn- total-count-for [build-data-entries]
  {:totalCount (count build-data-entries)})

(defn- failed-count-for [build-data-entries]
  (if-some [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:failedCount (jobinfo/fail-count builds)}))

(defn- flaky-count-for [build-data-entries]
  (if-some [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:flakyCount (jobinfo/flaky-build-count builds)}))

(defn- summary-for [build-results job-name from-timestamp]
  (let [build-data-entries (results/builds build-results job-name from-timestamp)]
    (merge (average-runtime-for build-data-entries)
           (total-count-for build-data-entries)
           (failed-count-for build-data-entries)
           (flaky-count-for build-data-entries))))

(defn get-jobs [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)
        build-summaries (map #(summary-for build-results % from-timestamp) job-names)
        build-summary (zipmap job-names build-summaries)]
    (if (= (:mime accept) :json)
      (http/respond-with-json build-summary)
      (http/respond-with-csv
       (csv/export-table ["job" "averageRuntime" "totalCount" "failedCount" "flakyCount"]
                         (map (fn [[job-name {:keys [averageRuntime totalCount failedCount flakyCount]}]]
                                [job-name
                                 (csv/format-duration averageRuntime)
                                 totalCount
                                 failedCount
                                 flakyCount])
                              build-summary))))))
