(ns buildviz.controllers.testcases
  (:require [buildviz.analyse.testsuites :as testsuites]
            [buildviz.data
             [junit-xml :as junit-xml]
             [results :as results]]
            [buildviz.util
             [csv :as csv]
             [http :as http]]))

(defn- test-runs [build-results job-name from-timestamp]
  (map junit-xml/parse-testsuites
       (results/chronological-tests build-results job-name from-timestamp)))

(defn- testcase-info [build-results job-name from-timestamp]
  (testsuites/aggregate-testcase-info (test-runs build-results job-name from-timestamp)))

(defn- flat-testcase-info [build-results job-name from-timestamp]
  (->> (testsuites/aggregate-testcase-info-as-list (test-runs build-results job-name from-timestamp))
       (map (fn [{:keys [testsuite classname name average-runtime failed-count]}]
              [(csv/format-duration average-runtime)
               failed-count
               job-name
               (csv/serialize-nested-testsuites testsuite)
               classname
               name]))))

(defn get-testcases [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> job-names
                                   (map #(testcase-info build-results % from-timestamp))
                                   (zipmap job-names)
                                   (remove (fn [[job-name testcases]]
                                             (empty? testcases)))
                                   (map (fn [[job-name testcases]]
                                          {:job-name job-name
                                           :children testcases}))))
      (http/respond-with-csv (csv/export-table
                              ["averageRuntime" "failedCount" "job" "testsuite" "classname" "name"]
                              (mapcat #(flat-testcase-info build-results % from-timestamp) job-names))))))
