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

(defn- testcase [testcase-elem]
  (let [testcase {:name (item-name testcase-elem)
                  :status (parse-status testcase-elem)}]
    (if-let [runtime (parse-runtime testcase-elem)]
      (assoc testcase :runtime runtime)
      testcase)))

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


(defn- rolled-out-testcase [suite-id testcase]
  (let [testcase-id (conj suite-id (:name testcase))
        testcase-content (dissoc testcase :name)]
    (vector testcase-id testcase-content)))

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


(defn accumulate-testsuite-failures [test-runs]
  (->> (mapcat unroll-testcases test-runs)
       (failed-testcase-ids)
       (frequencies)
       (seq)
       (map (fn [[testcase-id failedCount]]
              [testcase-id {:failedCount failedCount}]))
       (into {})
       (build-suite-hierarchy)
       (testsuites-map->list)))


(defn- testcase-runtime [unrolled-testcases]
  (map (fn [[testcase-id testcase]] [testcase-id (:runtime testcase)])
       unrolled-testcases))

(defn- avg [series]
  (/ (reduce + series) (count series)))

(defn- average-runtimes [testcase-runtimes]
  (->> (group-by first testcase-runtimes)
       (seq)
       (map (fn [[testcase-id grouped-entries]]
              [testcase-id (map second grouped-entries)]))
       (map (fn [[testcase-id runtimes]]
              [testcase-id {:averageRuntime (avg runtimes)}]))
       (into {})))

(defn average-testsuite-duration [test-runs]
  (->> (mapcat unroll-testcases test-runs)
       (testcase-runtime)
       (average-runtimes)
       (build-suite-hierarchy)
       (testsuites-map->list)
       ))
