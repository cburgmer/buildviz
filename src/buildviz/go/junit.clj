(ns buildviz.go.junit
  (:require [clojure.data.xml :as xml]
            [clojure.tools.logging :as log]))

(defn- testsuite? [elem]
  (= :testsuite (:tag elem)))

(defn- testsuite-list [junit-xml]
  (let [root (xml/parse-str junit-xml)]
    (if (testsuite? root)
      (list root)
      (:content root))))

(defn merge-junit-xml [junit-xml-list]
  (when junit-xml-list
    (xml/emit-str (apply xml/element
                         (cons :testsuites
                               (cons {}
                                     (mapcat testsuite-list junit-xml-list)))))))
