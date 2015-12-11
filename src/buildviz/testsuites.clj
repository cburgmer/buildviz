(ns buildviz.testsuites
  (:require [buildviz
             [jobinfo :as jobinfo]
             [junit-xml :as junit-xml]
             [math :as math]
             [testsuite-transform :as transform]]))

(defn- average-runtime-for-testcase-runs [testcases]
  (when-let [runtimes (seq (remove nil? (map :runtime testcases)))]
    (math/avg runtimes)))

(defn aggregate-testcase-runs [testcases]
  (let [failed-count (count (remove junit-xml/is-ok? testcases))
        aggregated {:failedCount failed-count}]
    (if-let [average-runtime (average-runtime-for-testcase-runs testcases)]
      (assoc aggregated :averageRuntime average-runtime)
      aggregated)))

(defn- aggregate-runs [unrolled-testcases]
  (->> unrolled-testcases
       (group-by first)
       (map (fn [[testcase-id group]]
              [testcase-id (->> group
                                (map last)
                                aggregate-testcase-runs)]))
       (into {})))

(defn- aggregated-info-by-testcase [test-runs]
  (aggregate-runs (transform/test-runs->testcase-list test-runs)))


(defn aggregate-testcase-info [test-runs]
  (->> (aggregated-info-by-testcase test-runs)
       transform/testcase-list->testsuite-tree))

(defn aggregate-testcase-info-as-list [test-runs]
  (->> (aggregated-info-by-testcase test-runs)
       (map (fn [[testcase-id {average-runtime :averageRuntime failed-count :failedCount}]]
              (transform/testcase->map [(apply vector testcase-id) {:average-runtime average-runtime
                                                                    :failed-count failed-count}])))))


(defn average-testclass-runs [testclasses]
  (if-let [average-runtime (average-runtime-for-testcase-runs testclasses)]
    {:averageRuntime average-runtime}
    {}))

(defn- average-runs [unrolled-testcases]
  (->> unrolled-testcases
       (group-by first)
       (map (fn [[testcase-id group]]
              [testcase-id (->> group
                                (map last)
                                average-testclass-runs)]))
       (into {})))

(defn- accumulated-runtime [testcases]
  (let [runtimes (map :runtime testcases)]
    (when (every? number? runtimes)
      (apply + runtimes))))

(defn- accumulate-runtime-by-class-for-testcases [testcases]
  (map (fn [[classname testcases]]
         {:name classname
          :runtime (accumulated-runtime testcases)})
       (group-by :classname testcases)))

(defn- accumulate-runtime-by-class [testsuite]
  (let [nested-suites (filter :children (:children testsuite))
        testcases (remove :children (:children testsuite))]
    (assoc testsuite
           :children
           (concat (map accumulate-runtime-by-class nested-suites)
                   (accumulate-runtime-by-class-for-testcases testcases)))))

(defn- average-runtimes-by-testclass [test-runs]
  (->> test-runs
       (map #(map accumulate-runtime-by-class %))
       transform/test-runs->testcase-list
       average-runs))


(defn average-testclass-runtime [test-runs]
  (transform/testcase-list->testsuite-tree (average-runtimes-by-testclass test-runs)))

(defn average-testclass-runtime-as-list [test-runs]
  (map transform/testclass->map
       (average-runtimes-by-testclass test-runs)))


(defn- find-flaky-build-groups [jobs]
  (->> (seq jobs)
       (map (fn [[build-id build-data]]
              (assoc build-data :id build-id)))
       (jobinfo/builds-grouped-by-same-inputs)
       (map #(group-by :outcome %))
       (filter #(< 1 (count %)))))

(defn- flaky-builds [jobs]
  (->> jobs
       find-flaky-build-groups
       (map #(get % "fail"))
       (apply concat)))

(defn- flaky-testcases-for-build [{build-id :id start :start} test-results-func]
  (->> (test-results-func build-id)
       transform/unroll-testsuites
       (remove (fn [[testcase-id testcase]] (junit-xml/is-ok? testcase)))
       (map (fn [[testcase-id {}]] [testcase-id {:build-id build-id :failure-time start}]))))

(defn- flaky-testcase-summary [unrolled-testcases]
  (let [build-infos (map last unrolled-testcases)
        last-build (apply max-key :failure-time build-infos)]
    {:build-id (:build-id last-build)
     :latest-failure (:failure-time last-build)
     :flaky-count (count unrolled-testcases)}))

(defn- flaky-testcase-summaries [unrolled-testcases]
  (->> unrolled-testcases
       (group-by (fn [[testcase-id {}]] testcase-id))
       (map (fn [[testcase-id testcases]]
              [testcase-id (flaky-testcase-summary testcases)]))))

(defn flaky-testcases-as-list [builds test-results-func]
  (->> builds
       flaky-builds
       (mapcat #(flaky-testcases-for-build % test-results-func))
       flaky-testcase-summaries
       (map transform/testcase->map)))
