(ns buildviz.data.junit-xml
  (:require [clojure.data.xml :as xml]))

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

(defn- assert-not-nil [value msg]
  (if (nil? value)
    (throw (IllegalArgumentException. msg)))
  value)

(defn- parse-name [elem]
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
  (-> {:name (assert-not-nil (parse-name testcase-elem) "No name given for testcase")
       :status (parse-status testcase-elem)
       :classname (assert-not-nil (parse-classname testcase-elem) "No classname given for testcase")}
      (add-runtime testcase-elem)))

(defn- testsuite? [elem]
  (= :testsuite (:tag elem)))

(defn- testcase? [elem]
  (= :testcase (:tag elem)))

(defn- parseable-content [elem]
  (let [children (:content elem)]
    (filter #(or (testcase? %) (testsuite? %)) children)))

(declare parse-testsuite)

(defn- testsuite [testsuite-elem]
  {:name (assert-not-nil (parse-name testsuite-elem) "No name given for testsuite (or invalid element)")
   :children (map parse-testsuite
                  (parseable-content testsuite-elem))})

(defn- parse-testsuite [elem]
  (if (testsuite? elem)
    (testsuite elem)
    (testcase elem)))

(defn- all-testsuites [root]
  (if (testsuite? root)
    (list root)
    (:content root)))

(defn parse-testsuites [junit-xml-result]
  (let [root (xml/parse-str junit-xml-result)]
    (map parse-testsuite
         (all-testsuites root))))



(declare element->node)

(defn format-runtime-in-millis [duration]
  (when-not (nil? duration)
    (format "%.3f" (float (/ duration 1000)))))

(defn- testcase-status->node [status]
  (case status
    "fail" (xml/element "failure")
    "error" (xml/element "error")
    "skipped" (xml/element "skipped")
    nil))

(defn- testcase->node [{:keys [:name :classname :runtime :status]}]
  (let [status-element (testcase-status->node status)
        mandatory-testcase-attributes {:name name :time (format-runtime-in-millis runtime)}
        testcase-attributes (if classname
                              (assoc mandatory-testcase-attributes :classname classname)
                              mandatory-testcase-attributes)]
    (apply xml/element (list* :testcase
                              testcase-attributes
                              (list status-element)))))

(defn- testsuite->node [{:keys [:name :children]}]
  (apply xml/element (list* :testsuite
                            {:name name}
                            (map element->node children))))

(defn- element->node [element]
  (if (contains? element :children)
    (testsuite->node element)
    (testcase->node element)))

(defn- testsuites->node [testsuites]
  (apply xml/element (list* :testsuites {} (map element->node testsuites))))

(defn serialize-testsuites [testsuites]
  (xml/emit-str (testsuites->node testsuites)))
