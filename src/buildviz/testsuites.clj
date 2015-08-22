(ns buildviz.testsuites
  (:require [buildviz.junit-xml :as junit-xml]))


(declare testsuites-map->list)

(defn- testsuite-entry-for [name children-map]
  {:name name
   :children (testsuites-map->list children-map)})

(defn- is-leaf-node? [children]
  (not (some map? (vals children))))

(defn- testsuites-map->list [testsuites]
  (reduce-kv (fn [suites name children]
               (conj suites (if (is-leaf-node? children)
                              (assoc children :name name)
                              (testsuite-entry-for name children))))
             []
             testsuites))


(defn- testcase-id [suite-id testcase]
  (let [name (:name testcase)]
    (if-let [classname (:classname testcase)]
      (conj suite-id classname name)
      (conj suite-id name))))

(defn- rolled-out-testcase [suite-id testcase]
  (let [testcase-content (dissoc testcase :name :classname)]
    (vector (testcase-id suite-id testcase)
            testcase-content)))

(defn- unroll-testcases-for-suite [parent-suite-id entry]
  (if-let [children (:children entry)]
    (let [suite-name (:name entry)
          suite-id (conj parent-suite-id suite-name)]
      (mapcat (partial unroll-testcases-for-suite suite-id) children))
    (list (rolled-out-testcase parent-suite-id entry))))

(defn- unroll-testsuites [testsuites]
  (mapcat (partial unroll-testcases-for-suite []) testsuites))


(defn- failed-testcase-ids [unrolled-testcases]
  (map #(first %)
       (filter #(not (junit-xml/is-ok? (last %)))
               unrolled-testcases)))


(defn- assoc-testcase-entry [testsuite testcase-id testcase-data]
  (let [testcase {(peek testcase-id) testcase-data}
        suite-path (pop testcase-id)]
    (update-in testsuite suite-path merge testcase)))

(defn- build-suite-hierarchy-recursively [testsuite testcase-entries]
  (if-let [next-testcase (first testcase-entries)]
    (let [testcase-id (key next-testcase)
          fail-count (val next-testcase)]
      (recur
       (assoc-testcase-entry testsuite testcase-id fail-count)
       (rest testcase-entries)))
    testsuite))

(defn- build-suite-hierarchy [testcase-entries]
  (build-suite-hierarchy-recursively {} testcase-entries))


(defn- build-testcase-data-with-failures [unrolled-testcase-map]
  (zipmap (keys unrolled-testcase-map)
          (map #(assoc {} :failedCount %) (vals unrolled-testcase-map))))

(defn- accumulate-testsuite-failures-by-testcase [test-runs]
  (->> (mapcat unroll-testsuites test-runs)
       failed-testcase-ids
       frequencies
       build-testcase-data-with-failures))

(defn accumulate-testsuite-failures [test-runs]
  (->> (accumulate-testsuite-failures-by-testcase test-runs)
       build-suite-hierarchy
       testsuites-map->list))

(defn accumulate-testsuite-failures-as-list [test-runs]
  (->> (accumulate-testsuite-failures-by-testcase test-runs)
       (map (fn [[testcase-id {failed-count :failedCount}]]
              {:testsuite (pop (pop testcase-id))
               :classname (last (pop testcase-id))
               :name (last testcase-id)
               :failedCount failed-count}))))


(defn- extract-runtime [unrolled-testcases]
  (map (fn [[testcase-id testcase]] [testcase-id (:runtime testcase)])
       unrolled-testcases))

(defn- avg [series]
  (Math/round (float (/ (reduce + series) (count series)))))


(defn- average-runtime-for-testcase-runs [testcases]
  (if-let [runtimes (seq (keep second testcases))]
    {:averageRuntime (avg runtimes)}
    {}))

(defn- average-runtimes [testcase-runtimes]
  (let [groups (group-by first testcase-runtimes)]
    (zipmap (keys groups)
            (map average-runtime-for-testcase-runs (vals groups)))))

(defn- average-runtimes-by-testcase [test-runs]
  (->> (mapcat unroll-testsuites test-runs)
       extract-runtime
       average-runtimes))

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
        testcases (filter (complement :children) (:children testsuite))]
    (assoc testsuite
           :children
           (concat (map accumulate-runtime-by-class nested-suites)
                   (accumulate-runtime-by-class-for-testcases testcases)))))

(defn- average-runtimes-by-testclass [test-runs]
  (->> (map #(map accumulate-runtime-by-class %) test-runs)
       (mapcat unroll-testsuites)
       extract-runtime
       average-runtimes))


(defn average-testcase-runtime [test-runs]
  (->> (average-runtimes-by-testcase test-runs)
       build-suite-hierarchy
       testsuites-map->list))

(defn average-testcase-runtime-as-list [test-runs]
  (->> (average-runtimes-by-testcase test-runs)
       (map (fn [[testcase-id {average-runtime :averageRuntime}]]
              {:testsuite (pop (pop testcase-id))
               :classname (last (pop testcase-id))
               :name (last testcase-id)
               :averageRuntime average-runtime}))))

(defn average-testclass-runtime [test-runs]
  (->> (average-runtimes-by-testclass test-runs)
       build-suite-hierarchy
       testsuites-map->list))

(defn average-testclass-runtime-as-list [test-runs]
  (->> (average-runtimes-by-testclass test-runs)
       (map (fn [[testclass-id {average-runtime :averageRuntime}]]
              {:testsuite (pop testclass-id)
               :classname (last testclass-id)
               :averageRuntime average-runtime}))))


(defn- find-flaky-build-groups [jobs]
  (->> (seq jobs)
       (map (fn [[build-id build-data]]
              (assoc build-data :id build-id)))
       (group-by :inputs)
       vals
       (map #(group-by :outcome %))
       (filter #(< 1 (count %)))))

(defn- flaky-builds [jobs]
  (->> jobs
       find-flaky-build-groups
       (map #(get % "fail"))
       (apply concat)))

(defn- flaky-testcases-for-build [{build-id :id start :start} test-results-func]
  (->> (test-results-func build-id)
       unroll-testsuites
       (filter (fn [[testcase-id testcase]] (not (junit-xml/is-ok? testcase))))
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
       (map (fn [[testcase-id summary]]
              (assoc summary
                     :testsuite (pop (pop testcase-id))
                     :classname (last (pop testcase-id))
                     :name (last testcase-id))))))
