(ns buildviz.controllers.testclasses
  (:require [buildviz.analyse.testsuites :as testsuites]
            [buildviz.data.results :as results]
            [buildviz.junit-xml :as junit-xml]
            [buildviz.util
             [csv :as csv]
             [http :as http]]))

(defn- test-runs [build-results job-name from-timestamp]
  (map junit-xml/parse-testsuites
       (results/chronological-tests build-results job-name from-timestamp)))

(defn- testclass-runtimes [build-results job-name from-timestamp]
  (testsuites/average-testclass-runtime (test-runs build-results job-name from-timestamp)))

(defn- flat-testclass-runtimes [build-results job-name from-timestamp]
  (->> (testsuites/average-testclass-runtime-as-list (test-runs build-results job-name from-timestamp))
       (map (fn [{:keys [testsuite classname average-runtime]}]
              [(csv/format-duration average-runtime)
               job-name
               (csv/serialize-nested-testsuites testsuite)
               classname]))))

(defn get-testclasses [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> job-names
                                   (map #(testclass-runtimes build-results % from-timestamp))
                                   (zipmap job-names)
                                   (remove (fn [[job-name testcases]]
                                             (empty? testcases)))
                                   (map (fn [[job-name testcases]]
                                          {:job-name job-name
                                           :children testcases}))))
      (http/respond-with-csv (csv/export-table
                              ["averageRuntime" "job" "testsuite" "classname"]
                              (mapcat #(flat-testclass-runtimes build-results % from-timestamp) job-names))))))
