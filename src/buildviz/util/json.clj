(ns buildviz.util.json
  (:require [cheshire.core :as j]
            [wharf.core :as wharf]))

(defn to-string [data]
  (->> data
       (wharf/transform-keys (comp wharf/hyphen->lower-camel name))
       j/generate-string))

(defn from-string [json-string]
  (->> json-string
       j/parse-string
       (wharf/transform-keys (comp keyword clojure.string/lower-case wharf/camel->hyphen))))

(defn from-sequence [json-sequence]
  (->> json-sequence
       j/parsed-seq
       (wharf/transform-keys (comp keyword clojure.string/lower-case wharf/camel->hyphen))))
