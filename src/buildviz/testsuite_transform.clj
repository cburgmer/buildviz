(ns buildviz.testsuite-transform
  (:require [buildviz.junit-xml :as junit-xml]))

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

(defn unroll-testsuites [testsuites]
  (mapcat (partial unroll-testcases-for-suite []) testsuites))


(defn- accumulated-testcase [testcases]
  (let [runtimes (filter some? (map :runtime testcases))
        failed-testcase-status (remove junit-xml/is-ok?
                                       (map :status testcases))]
    {:runtime (when (seq runtimes)
                (reduce + runtimes))
     :status (if (empty? failed-testcase-status)
               (:status (first testcases))
               (first failed-testcase-status))}))

(defn- accumulate-testcases-with-duplicate-names [unrolled-entries]
  (->> unrolled-entries
       (group-by first)
       (map (fn [[entry-id duplicate-entry-list]]
              [entry-id (->> duplicate-entry-list
                             (map last)
                             accumulated-testcase)]))))


(defn test-runs->testcase-list [test-runs]
  (->> test-runs
       (map unroll-testsuites)
       (mapcat accumulate-testcases-with-duplicate-names)))


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


(defn testcase-list->testsuite-tree [testcase-list]
  (->> testcase-list
       build-suite-hierarchy
       testsuites-map->list))


(defn testclass->map [[testclass-id statistics]]
  (merge {:testsuite (pop testclass-id)
          :classname (last testclass-id)}
         statistics))

(defn testcase->map [[testcase-id statistics]]
  (-> (testclass->map [(pop testcase-id) statistics])
      (assoc :name (last testcase-id))))
