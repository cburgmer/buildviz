(ns buildviz.util.csv
  (:require [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]
            [clojure.string :as str]))

(def separator ",")

(defn- in-quotes [value]
  (when-not (nil? value)
    (str/join ["\"" (str/escape value {\" "\"\""}) "\""])))

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
       (str/join separator)))


;; http://stackoverflow.com/questions/804118/best-timestamp-format-for-csv-excel
(def excel-datetime-formatter (tf/formatter "yyyy-MM-dd HH:mm:ss" (t/default-time-zone)))

(defn format-timestamp [timestamp]
  (when-not (nil? timestamp)
    (tf/unparse excel-datetime-formatter (tc/from-long (long timestamp)))))


(def day-in-millis (* 24 60 60 1000))

(defn format-duration [duration]
  (when-not (nil? duration)
    (format "%.8f" (float (/ duration day-in-millis)))))

(defn export-table [header entries]
  (str/join [(str/join "\n" (cons (export header)
                          (map export entries)))
         "\n"]))

(defn serialize-nested-testsuites [testsuite-id]
  (str/join ": " testsuite-id))
