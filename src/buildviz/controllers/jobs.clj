(ns buildviz.controllers.jobs
  (:require [buildviz.analyse.builds :as builds]
            [buildviz.data.results :as results]
            [buildviz.util
             [csv :as csv]
             [http :as http]]))

(defn- average-runtime-for [builds]
  (when-let [avg-runtime (builds/average-runtime builds)]
    {:average-runtime avg-runtime}))

(defn- total-count-for [builds]
  {:total-count (count builds)})

(defn- failed-count-for [builds]
  (when-let [builds (seq (builds/builds-with-outcome builds))]
    {:failed-count (builds/fail-count builds)}))

(defn- flaky-count-for [builds]
  (when-let [builds (seq (builds/builds-with-outcome builds))]
    {:flaky-count (builds/flaky-build-count builds)}))

(defn- summary-for [build-results job-name from-timestamp]
  (let [builds (results/builds build-results job-name from-timestamp)]
    (merge (average-runtime-for builds)
           (total-count-for builds)
           (failed-count-for builds)
           (flaky-count-for builds))))

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
