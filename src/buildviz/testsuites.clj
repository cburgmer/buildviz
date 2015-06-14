(ns buildviz.testsuites
  (:require [clojure.xml :as xml]))

(defn- is-failure? [testcase-elem]
  (some #(= :failure (:tag %))
        (:content testcase-elem)))

(defn- item-name [elem]
  (:name (:attrs elem)))

(defn- testcase [testcase-elem]
  (let [status (if (is-failure? testcase-elem)
                 :fail
                 :pass)]
    {:name (item-name testcase-elem)
     :status status}))

(defn- testsuite [testsuite-elem]
  {:name (item-name testsuite-elem)
   :children (map testcase (:content testsuite-elem))})

(defn testsuites-for [junit-xml-result]
  (let [junit-xml-dom (xml/parse (java.io.ByteArrayInputStream. (.getBytes junit-xml-result)))
        testsuites (:content junit-xml-dom)]
    (map testsuite testsuites)))



(defn- testcase-entries-for [children-map]
  (reduce-kv (fn [children name entry-map]
               (conj children (assoc entry-map :name name)))
             []
             children-map))

(defn- testsuite-entry-for [name children-map]
  {:name name
   :children (testcase-entries-for children-map)})

(defn- testsuites-map->list [testsuites]
  (reduce-kv (fn [suites name children]
               (conj suites (testsuite-entry-for name children)))
             []
             testsuites))


(defn- filter-failed-tests-for-children [children]
  (filter #(= :fail (:status %)) children))

(defn- filter-failed-tests-for-suite [testsuite]
  (update-in testsuite
             [:children]
             filter-failed-tests-for-children))

(defn- filter-failed-tests [testsuites]
  (map filter-failed-tests-for-suite testsuites))


(defn- rollout-testcases-for-suite [suite]
  (let [suite-name (:name suite)
        children (:children suite)]
    (map #(vector suite-name (:name %)) children)))

(defn- rollout-testcases [testsuites]
  (mapcat rollout-testcases-for-suite testsuites))


(defn- assoc-testcase [testsuite testcase-key fail-count]
  (let [testcase {(last testcase-key) {:failedCount fail-count}}
        testsuite-name (first testcase-key)]
    (if (contains? testsuite testsuite-name)
      (update-in testsuite [testsuite-name] merge testcase )
      (assoc testsuite testsuite-name testcase))))

(defn- build-hierarchy-recursivley [testsuite testcase-fail-frequencies]
  (if-let [next-testcase (first testcase-fail-frequencies)]
    (let [testcase-key (key next-testcase)
          fail-count (val next-testcase)]
      (build-hierarchy-recursivley
       (assoc-testcase testsuite testcase-key fail-count)
       (rest testcase-fail-frequencies)))
    testsuite))

(defn- build-hierarchy [testcase-fail-frequencies]
  (build-hierarchy-recursivley {} testcase-fail-frequencies))


(defn accumulate-testsuite-failures [test-runs]
  (->> (map filter-failed-tests test-runs)
       (mapcat rollout-testcases)
       (frequencies)
       (build-hierarchy)
       (testsuites-map->list)))
