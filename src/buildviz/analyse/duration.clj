(ns buildviz.analyse.duration
  (:require [buildviz.util.math :as math]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]))

(def ^:private date-only-formatter (tf/formatter "yyyy-MM-dd" (t/default-time-zone)))

(defn- date-for [timestamp]
  (when timestamp
    (tf/unparse date-only-formatter (tc/from-long (long timestamp)))))

(defn- average-duration [builds]
  (math/avg (map :duration builds)))

(defn- average-duration-by-day [duration-entries]
  (->> duration-entries
       (group-by #(date-for (:end %)))
       (filter first)
       (map (fn [[date builds]]
              [date (average-duration builds)]))
       (into {})))

(defn average-by-day [duration-entries]
  (->> duration-entries
       (group-by :name)
       (map (fn [[name duration-entries]]
              [name (average-duration-by-day duration-entries)]))
       (into {})))
