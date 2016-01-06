(ns buildviz.controllers.jobs
  (:require [buildviz.analyse.builds :as builds]
            [buildviz.data.results :as results]
            [buildviz.util
             [csv :as csv]
             [http :as http]]))

(defn- average-runtime-for [build-data-entries]
  (if-let [avg-runtime (builds/average-runtime build-data-entries)]
    {:average-runtime avg-runtime}))

(defn- total-count-for [build-data-entries]
  {:total-count (count build-data-entries)})

(defn- failed-count-for [build-data-entries]
  (if-some [builds (seq (builds/builds-with-outcome build-data-entries))]
    {:failed-count (builds/fail-count builds)}))

(defn- flaky-count-for [build-data-entries]
  (if-some [builds (seq (builds/builds-with-outcome build-data-entries))]
    {:flaky-count (builds/flaky-build-count builds)}))

(defn- summary-for [build-results job-name from-timestamp]
  (let [build-data-entries (results/builds build-results job-name from-timestamp)]
    (merge (average-runtime-for build-data-entries)
           (total-count-for build-data-entries)
           (failed-count-for build-data-entries)
           (flaky-count-for build-data-entries))))

(defn get-jobs [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)
        job-entries (map (fn [job-name]
                           (assoc (summary-for build-results job-name from-timestamp)
                                  :job-name job-name))
                         job-names)]
    (if (= (:mime accept) :json)
      (http/respond-with-json job-entries)
      (http/respond-with-csv
       (csv/export-table ["job" "averageRuntime" "totalCount" "failedCount" "flakyCount"]
                         (map (fn [{:keys [job-name average-runtime total-count failed-count flaky-count]}]
                                [job-name
                                 (csv/format-duration average-runtime)
                                 total-count
                                 failed-count
                                 flaky-count])
                              job-entries))))))
