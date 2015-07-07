(ns buildviz.csv
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc])
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


;; http://stackoverflow.com/questions/804118/best-timestamp-format-for-csv-excel
(def excel-datetime-formatter (tf/formatter "yyyy-MM-dd HH:mm:ss" (t/default-time-zone)))

(defn format-timestamp [timestamp]
  (if (nil? timestamp)
    nil
    (tf/unparse excel-datetime-formatter (tc/from-long (long timestamp)))))


(defn export-table [header entries]
  (join [(join "\n" (cons (export header)
                          (map export entries)))
         "\n"]))
