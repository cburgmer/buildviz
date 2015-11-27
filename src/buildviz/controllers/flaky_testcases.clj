(ns buildviz.controllers.flaky-testcases
  (:require [buildviz
             [csv :as csv]
             [http :as http]
             [junit-xml :as junit-xml]
             [testsuites :as testsuites]]
            [buildviz.data.results :as results]))

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

(defn- flat-flaky-testcases [build-results job-name from-timestamp]
  (let [builds (builds-after-timestamp build-results job-name from-timestamp)
        test-lookup (partial test-results-for-build build-results job-name)]
    (->> (testsuites/flaky-testcases-as-list builds test-lookup)
         (map (fn [{testsuite :testsuite
                    classname :classname
                    name :name
                    build-id :build-id
                    latest-failure :latest-failure
                    flaky-count :flaky-count}]
                [(csv/format-timestamp latest-failure)
                 flaky-count
                 job-name
                 build-id
                 (csv/serialize-nested-testsuites testsuite)
                 classname
                 name])))))

(defn get-flaky-testclasses [build-results from-timestamp]
  (http/respond-with-csv (csv/export-table
                          ["latestFailure" "flakyCount" "job" "latestBuildId" "testsuite" "classname" "name"]
                          (mapcat #(flat-flaky-testcases build-results % from-timestamp)
                                  (results/job-names build-results)))))
