(ns buildviz.junit-xml
  (:require [clojure.xml :as xml]))

(defn is-ok? [{status :status}]
  (contains? #{:pass :skipped} status))

;; Parsing is following schema documented in http://llg.cubic.org/docs/junit/

(defn- is-failure? [testcase-elem]
  (some #(= :failure (:tag %))
        (:content testcase-elem)))

(defn- is-error? [testcase-elem]
  (some #(= :error (:tag %))
        (:content testcase-elem)))

(defn- is-skipped? [testcase-elem]
  (some #(= :skipped (:tag %))
        (:content testcase-elem)))

(defn- item-name [elem]
  (:name (:attrs elem)))

(defn- parse-runtime [testcase-elem]
  (if-let [time (:time (:attrs testcase-elem))]
    (Math/round (* 1000 (Float/parseFloat time)))))

(defn- parse-status [testcase-elem]
  (cond
    (is-failure? testcase-elem) :fail
    (is-error? testcase-elem) :error
    (is-skipped? testcase-elem) :skipped
    :else :pass))

(defn- parse-classname [testcase-elem]
  (if-let [classname (:classname (:attrs testcase-elem))]
    classname
    (:class (:attrs testcase-elem))))

(defn- add-runtime [testcase testcase-elem]
  (if-let [runtime (parse-runtime testcase-elem)]
    (assoc testcase :runtime runtime)
    testcase))

(defn- testcase [testcase-elem]
  (-> {:name (item-name testcase-elem)
       :status (parse-status testcase-elem)
       :classname (parse-classname testcase-elem)}
      (add-runtime testcase-elem)))

(declare parse-testsuite)

(defn- testsuite? [elem]
  (= :testsuite (:tag elem)))

(defn- testcase? [elem]
  (= :testcase (:tag elem)))

(defn- parseable-elements [elements]
  (filter #(or (testcase? %) (testsuite? %)) elements))

(defn- testsuite [testsuite-elem]
  {:name (item-name testsuite-elem)
   :children (map parse-testsuite
                  (parseable-elements (:content testsuite-elem)))})

(defn- parse-testsuite [elem]
  (if (testsuite? elem)
    (testsuite elem)
    (testcase elem)))

(defn parse-testsuites [junit-xml-result]
  (let [root (xml/parse (java.io.ByteArrayInputStream. (.getBytes junit-xml-result)))]
    (if (= :testsuites (:tag root))
      (map parse-testsuite
           (:content root))
      (list (parse-testsuite root)))))
