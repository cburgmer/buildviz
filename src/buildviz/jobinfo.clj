(ns buildviz.jobinfo
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]))

(defn builds-with-outcome [build-data-entries]
  (filter #(contains? % :outcome) build-data-entries))

;; flaky builds

(defn- outcomes-for-builds-grouped-by-input [build-data-entries]
  (->> (filter #(contains? % :inputs) build-data-entries)
       (group-by :inputs)
       vals
       (map #(map :outcome %))
       (map distinct)))

(defn flaky-build-count [build-data-entries]
  (->> (outcomes-for-builds-grouped-by-input build-data-entries)
       (map count)
       (filter #(< 1 %))
       count))

;; avg runtime

(defn- avg [series]
  (Math/round (float (/ (reduce + series) (count series)))))

(defn- runtime-for [build]
  (if (and (contains? build :start)
           (contains? build :end))
    (- (build :end) (build :start))))

(defn- build-runtime [build-data-entries]
  (filter (complement nil?)
          (map runtime-for build-data-entries)))

(defn average-runtime [build-data-entries]
  (if-let [runtimes (seq (build-runtime build-data-entries))]
    (avg runtimes)))


(def date-only-formatter (tf/formatter "yyyy-MM-dd" (t/default-time-zone)))

(defn- date-for [{end :end}]
  (tf/unparse date-only-formatter (tc/from-long end)))

(defn average-runtime-by-day [build-data-entries]
  (->> (group-by date-for build-data-entries)
       (map (fn [[date builds]] [date (average-runtime builds)]))
       (filter second)
       (into {})))

;; error count

(defn- failed-build? [build]
  (= "fail" (:outcome build)))

(defn fail-count [build-data-entries]
  (count (filter failed-build? build-data-entries)))
