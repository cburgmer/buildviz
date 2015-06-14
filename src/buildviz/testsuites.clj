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

(defn- testcase [testcase-elem]
  (let [status (if (is-failure? testcase-elem)
                 :fail
                 (if (is-error? testcase-elem)
                   :error
                   :pass))]
    {:name (item-name testcase-elem)
     :status status}))

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
  (let [junit-xml-dom (xml/parse (java.io.ByteArrayInputStream. (.getBytes junit-xml-result)))
        testsuites (:content junit-xml-dom)]
    (map parse-testsuite testsuites)))



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


(defn- rolled-out-testcase [suite-name testcase]
  (let [testcase-id (vector suite-name (:name testcase))
        testcase-content (dissoc testcase :name)]
    (vector testcase-id testcase-content)))

(defn- unroll-testcases-for-suite [suite]
  (let [suite-name (:name suite)
        children (:children suite)]
    (map #(rolled-out-testcase suite-name %) children)))

(defn- unroll-testcases [testsuites]
  (mapcat unroll-testcases-for-suite testsuites))


(defn- failed-testcase-ids [unrolled-testcases]
  (map #(first %)
       (filter #(not= :pass (:status (last %)))
               unrolled-testcases)))


(defn- assoc-testcase [testsuite testcase-id fail-count]
  (let [testcase {(last testcase-id) {:failedCount fail-count}}
        testsuite-name (first testcase-id)]
    (if (contains? testsuite testsuite-name)
      (update-in testsuite [testsuite-name] merge testcase )
      (assoc testsuite testsuite-name testcase))))

(defn- build-suite-hierarchy-recursivley [testsuite testcase-fail-frequencies]
  (if-let [next-testcase (first testcase-fail-frequencies)]
    (let [testcase-id (key next-testcase)
          fail-count (val next-testcase)]
      (recur
       (assoc-testcase testsuite testcase-id fail-count)
       (rest testcase-fail-frequencies)))
    testsuite))

(defn- build-suite-hierarchy [testcase-fail-frequencies]
  (build-suite-hierarchy-recursivley {} testcase-fail-frequencies))


(defn accumulate-testsuite-failures [test-runs]
  (->> (mapcat unroll-testcases test-runs)
       (failed-testcase-ids)
       (frequencies)
       (build-suite-hierarchy)
       (testsuites-map->list)))
