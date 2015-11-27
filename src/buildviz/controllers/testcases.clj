(ns buildviz.controllers.testcases
  (:require [buildviz
             [csv :as csv]
             [http :as http]
             [junit-xml :as junit-xml]
             [testsuites :as testsuites]]
            [buildviz.data.results :as results]))

(defn- test-runs [build-results job-name from-timestamp]
  (map junit-xml/parse-testsuites
       (results/chronological-tests build-results job-name from-timestamp)))

(defn- testcase-runtimes [build-results job-name from-timestamp]
  {:children (testsuites/average-testcase-runtime (test-runs build-results job-name from-timestamp))})

(defn- flat-testcase-runtimes [build-results job-name from-timestamp]
  (->> (testsuites/average-testcase-runtime-as-list (test-runs build-results job-name from-timestamp))
       (map (fn [{testsuite :testsuite classname :classname name :name average-runtime :averageRuntime}]
              [(csv/format-duration average-runtime)
               job-name
               (csv/serialize-nested-testsuites testsuite)
               classname
               name]))))

(defn get-testcases [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> job-names
                                   (map #(testcase-runtimes build-results % from-timestamp))
                                   (zipmap job-names)
                                   (remove (fn [[job-name testcases]] (empty? (:children testcases))))
                                   (into {})))
      (http/respond-with-csv (csv/export-table
                              ["averageRuntime" "job" "testsuite" "classname" "name"]
                              (mapcat #(flat-testcase-runtimes build-results % from-timestamp) job-names))))))
