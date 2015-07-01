(ns buildviz.csv
  (:use [clojure.string :only (join escape)]))

(def separator ",")

(defn- in-quotes [value]
  (when-not (nil? value)
    (join ["\"" (escape value {\" "\"\""}) "\""])))

(defn- needs-quoting? [value]
  (or (.contains value separator)
      (.contains value "\"")))

(defn- quote-separator [value]
  (if (and (string? value)
           (needs-quoting? value))
    (in-quotes value)
    value))

(defn export [values]
  (->> (map quote-separator values)
       (join separator)))
