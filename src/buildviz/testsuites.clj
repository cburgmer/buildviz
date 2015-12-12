(ns buildviz.testsuites
  (:require [buildviz
             [jobinfo :as jobinfo]
             [junit-xml :as junit-xml]
             [math :as math]
             [testsuite-transform :as transform]]))

(defn- avg-with-nil [values]
  (when-let [non-nil-values (seq (remove nil? values))]
    (math/avg non-nil-values)))

(defn average-runtime [testclasses]
  (if-let [average-runtime (avg-with-nil (map :runtime testclasses))]
    {:average-runtime average-runtime}
    {}))

(defn aggregate-testcase-runs [testcases]
  (let [failed-count (count (remove junit-xml/is-ok? testcases))]
    (assoc (average-runtime testcases)
           :failed-count failed-count)))

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
       (map transform/testcase->map)))


(defn- average-runs [unrolled-testcases]
  (->> unrolled-testcases
       (group-by first)
       (map (fn [[testcase-id group]]
              [testcase-id (->> group
                                (map last)
                                average-runtime)]))
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


(defn- find-flaky-build-candidates [builds]
  (->> (seq builds)
       (map (fn [[build-id build-data]]
              (assoc build-data :id build-id)))
       (jobinfo/builds-grouped-by-same-inputs)
       (filter #(< 1 (count %)))))

(defn- unrolled-testcases [build test-results-func]
  (->> (test-results-func (:id build))
       transform/unroll-testsuites
       (map (fn [[testcase-id testcase]]
              [testcase-id {:build build
                            :ok? (junit-xml/is-ok? testcase)}]))))

(defn- flaky-testcases-for-builds [builds test-results-func]
  (->> builds
       (mapcat (fn [build]
                 (unrolled-testcases build test-results-func)))
       (group-by (fn [[testcase-id _]] testcase-id))
       vals
       (map (fn [testcases]
              (group-by (fn [[testcase-id {ok? :ok?}]] ok?)
                        testcases)))
       (filter (fn [testcase-result-groups]
                 (< 1 (count testcase-result-groups))))
       (mapcat (fn [testcase-result-groups]
              (get testcase-result-groups false)))))

(defn- flaky-testcase-summary [unrolled-testcases]
  (let [testcase-data (map last unrolled-testcases)
        last-builds-testcase (apply max-key #(get-in % [:build :start]) testcase-data)]
    {:build-id (get-in last-builds-testcase [:build :id])
     :latest-failure (get-in last-builds-testcase [:build :start])
     :flaky-count (count unrolled-testcases)}))

(defn- flaky-testcase-summaries [unrolled-testcases]
  (->> unrolled-testcases
       (group-by (fn [[testcase-id {}]] testcase-id))
       (map (fn [[testcase-id testcases]]
              [testcase-id (flaky-testcase-summary testcases)]))))

(defn flaky-testcases-as-list [builds test-results-func]
  (->> builds
       find-flaky-build-candidates
       (mapcat #(flaky-testcases-for-builds % test-results-func))
       flaky-testcase-summaries
       (map transform/testcase->map)))
