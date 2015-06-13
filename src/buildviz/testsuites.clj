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



(defn- testsuite-child-as-map [children]
  (zipmap (map :name children)
          (map #(dissoc % :name) children)))

(defn- testsuites-list->map [testsuites]
  (zipmap (map :name testsuites)
          (map testsuite-child-as-map
               (map :children testsuites))))


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


(defn- apply-fail-count-for-case [testcase]
  (assoc testcase :failedCount 1))

(defn- apply-fail-count-for-suite [testsuite]
  (update-in testsuite
             [:children]
             (fn [children] (map apply-fail-count-for-case children))))

(defn- apply-fail-count [testsuites]
  (map apply-fail-count-for-suite testsuites))


(defn- dissoc-status [testcase]
  (dissoc testcase :status))

(defn- filter-failed-tests-for-children [children]
  (map dissoc-status
       (filter #(= :fail (:status %)) children)))

(defn- filter-failed-tests-for-suite [testsuite]
  (update-in testsuite
             [:children]
             filter-failed-tests-for-children))

(defn- filter-failed-tests [testsuites]
  (map filter-failed-tests-for-suite testsuites))


(defn accumulate-testsuite-failures [test-runs]
  (->> (map filter-failed-tests test-runs)
       (map apply-fail-count)
       (map testsuites-list->map)
       (apply merge-with merge)
       (testsuites-map->list)))
