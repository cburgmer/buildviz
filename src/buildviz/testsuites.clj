(ns buildviz.testsuites
  (:require [clojure.xml :as xml]))

(defn- is-failure? [testcase-elem]
  (some #(= :failure (:tag %))
        (:content testcase-elem)))

(defn- is-error? [testcase-elem]
  (some #(= :error (:tag %))
        (:content testcase-elem)))

(defn- item-name [elem]
  (:name (:attrs elem)))

(defn- parse-runtime [testcase-elem]
  (if-let [time (:time (:attrs testcase-elem))]
    (Math/round (* 1000 (Float/parseFloat time)))))

(defn- parse-status [testcase-elem]
  (if (is-failure? testcase-elem)
    :fail
    (if (is-error? testcase-elem)
      :error
      :pass)))

(defn- add-runtime [testcase testcase-elem]
  (if-let [runtime (parse-runtime testcase-elem)]
    (assoc testcase :runtime runtime)
    testcase))

(defn- add-class [testcase testcase-elem]
  (if-let [classname (:classname (:attrs testcase-elem))]
    (assoc testcase :classname classname)
    testcase))

(defn- testcase [testcase-elem]
  (-> {:name (item-name testcase-elem)
       :status (parse-status testcase-elem)}
      (add-runtime testcase-elem)
      (add-class testcase-elem)))

(declare parse-testsuite)

(defn- testsuite [testsuite-elem]
  {:name (item-name testsuite-elem)
   :children (map parse-testsuite (:content testsuite-elem))})

(defn- testsuite? [elem]
  (= :testsuite (:tag elem)))

(defn- parse-testsuite [elem]
  (if (testsuite? elem)
    (testsuite elem)
    (testcase elem)))

(defn testsuites-for [junit-xml-result]
  (let [root (xml/parse (java.io.ByteArrayInputStream. (.getBytes junit-xml-result)))]
    (if (= :testsuites (:tag root))
      (map parse-testsuite
           (:content root))
      (list (parse-testsuite root)))))




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
  (let [testcase-content (dissoc testcase :name :class)]
    (vector (testcase-id suite-id testcase)
            testcase-content)))

(defn- unroll-testcases-for-suite [parent-suite-id entry]
  (if-let [children (:children entry)]
    (let [suite-name (:name entry)
          suite-id (conj parent-suite-id suite-name)]
      (mapcat (partial unroll-testcases-for-suite suite-id) children))
    (list (rolled-out-testcase parent-suite-id entry))))

(defn- unroll-testcases [testsuites]
  (mapcat (partial unroll-testcases-for-suite []) testsuites))


(defn- failed-testcase-ids [unrolled-testcases]
  (map #(first %)
       (filter #(not= :pass (:status (last %)))
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

(defn accumulate-testsuite-failures [test-runs]
  (->> (mapcat unroll-testcases test-runs)
       failed-testcase-ids
       frequencies
       build-testcase-data-with-failures
       build-suite-hierarchy
       testsuites-map->list))


(defn- testcase-runtime [unrolled-testcases]
  (map (fn [[testcase-id testcase]] [testcase-id (:runtime testcase)])
       unrolled-testcases))

(defn- avg [series]
  (/ (reduce + series) (count series)))


(defn- average-runtime-for-testcase-runs [testcases]
  (if-let [runtimes (seq (keep second testcases))]
    {:averageRuntime (avg runtimes)}
    {}))

(defn- average-runtimes [testcase-runtimes]
  (let [groups (group-by first testcase-runtimes)]
    (zipmap (keys groups)
            (map average-runtime-for-testcase-runs (vals groups)))))

(defn average-testsuite-runtime [test-runs]
  (->> (mapcat unroll-testcases test-runs)
       testcase-runtime
       average-runtimes
       build-suite-hierarchy
       testsuites-map->list))
