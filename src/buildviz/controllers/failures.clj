(ns buildviz.controllers.failures
  (:require [buildviz
             [csv :as csv]
             [http :as http]
             [jobinfo :as jobinfo]
             [junit-xml :as junit-xml]
             [testsuites :as testsuites]]
            [buildviz.data.results :as results]))

(defn- failed-count-for [build-data-entries]
  (if-some [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:failedCount (jobinfo/fail-count builds)}))

(defn- test-runs [build-results job-name from-timestamp]
  (map junit-xml/parse-testsuites
       (results/chronological-tests build-results job-name from-timestamp)))

(defn- failures-for [build-results job-name from-timestamp]
  (when-some [failed-tests (seq (testsuites/accumulate-testsuite-failures (test-runs build-results job-name from-timestamp)))]
    (let [build-data-entries (results/builds build-results job-name from-timestamp)]
      {job-name (merge {:children failed-tests}
                       (failed-count-for build-data-entries))})))

(defn- failures-as-list [build-results job-name from-timestamp]
  (when-some [test-results (seq (test-runs build-results job-name from-timestamp))]
    (->> test-results
         (testsuites/accumulate-testsuite-failures-as-list)
         (map (fn [{testsuite :testsuite classname :classname name :name failed-count :failedCount}]
                [failed-count
                 job-name
                 (csv/serialize-nested-testsuites testsuite)
                 classname
                 name])))))

(defn get-failures [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)]
    (if (= (:mime accept) :json)
      (let [failures (map #(failures-for build-results % from-timestamp) job-names)]
        (http/respond-with-json (into {} (apply merge failures))))
      (http/respond-with-csv (csv/export-table ["failedCount" "job" "testsuite" "classname" "name"]
                                               (mapcat #(failures-as-list build-results % from-timestamp) job-names))))))
