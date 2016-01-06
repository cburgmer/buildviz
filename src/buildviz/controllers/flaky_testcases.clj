(ns buildviz.controllers.flaky-testcases
  (:require [buildviz.analyse.testsuites :as testsuites]
            [buildviz.data
             [junit-xml :as junit-xml]
             [results :as results]]
            [buildviz.util
             [csv :as csv]
             [http :as http]]))

(defn- test-results-for-build [build-results job-name build-id]
  (if-let [test-results (results/tests build-results job-name build-id)]
    (junit-xml/parse-testsuites test-results)))

(defn- builds-after-timestamp [build-results job-name from-timestamp]
  (let [builds (get @(:builds build-results) job-name)]
    (if-not (nil? from-timestamp)
      (->> builds
           (remove (fn [[build-id build]] (nil? (:start build))))
           (filter (fn [[build-id build]] (>= (:start build) from-timestamp)))
           (into {}))
      builds)))

(defn- flaky-testcases [build-results job-name from-timestamp]
  (let [builds (builds-after-timestamp build-results job-name from-timestamp)
        test-lookup (partial test-results-for-build build-results job-name)]
    (testsuites/flaky-testcases builds test-lookup)))

(defn- flat-flaky-testcases [build-results job-name from-timestamp]
  (let [builds (builds-after-timestamp build-results job-name from-timestamp)
        test-lookup (partial test-results-for-build build-results job-name)]
    (->> (testsuites/flaky-testcases-as-list builds test-lookup)
         (map (fn [{:keys [testsuite classname name latest-build-id latest-failure flaky-count]}]
                [(csv/format-timestamp latest-failure)
                 flaky-count
                 job-name
                 latest-build-id
                 (csv/serialize-nested-testsuites testsuite)
                 classname
                 name])))))

(defn get-flaky-testclasses [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> job-names
                                   (map #(flaky-testcases build-results % from-timestamp))
                                   (zipmap job-names)
                                   (remove (fn [[job-name testcases]]
                                             (empty? testcases)))
                                   (map (fn [[job-name testcases]]
                                          {:job-name job-name
                                           :children testcases}))))
      (http/respond-with-csv (csv/export-table
                              ["latestFailure" "flakyCount" "job" "latestBuildId" "testsuite" "classname" "name"]
                              (mapcat #(flat-flaky-testcases build-results % from-timestamp)
                                      job-names))))))
