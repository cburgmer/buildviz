(ns buildviz.controllers.util
  (:require [buildviz.util.csv :as csv]))

(defn- remap-date-first [[key wait-times-by-day]]
  (map (fn [[day avg-wait-time]]
         [day {key avg-wait-time}])
       wait-times-by-day))

(defn- merge-wait-times [all-wait-times-by-day]
  (->> (mapcat remap-date-first all-wait-times-by-day)
       (group-by first)
       (map (fn [[date entries]]
              [date (apply merge (map second entries))]))))

(defn- durations-of-day [column-headers [date durations-by-column]]
  (->> (map durations-by-column column-headers)
       (map csv/format-duration)
       (cons date)))

(defn durations-as-table [durations]
  (let [column-headers (keys durations)]
    (csv/export-table (cons "date" column-headers)
                      (->> (merge-wait-times durations)
                           (sort-by first)
                           (map #(durations-of-day column-headers %))))))
