(ns buildviz.util.json
  (:require [cheshire.core :as j]
            [wharf.core :as wharf]))

(defn jsonize [collection]
  (wharf/transform-keys (comp wharf/hyphen->lower-camel name)
                        collection))

(defn to-string [data]
  (->> data
       jsonize
       j/generate-string))

(defn clojurize [json]
  (wharf/transform-keys (comp keyword clojure.string/lower-case wharf/camel->hyphen)
                        json))

(defn from-string [json-string]
  (->> json-string
       j/parse-string
       clojurize))
