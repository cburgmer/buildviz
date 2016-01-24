(ns buildviz.analyse.builds
  (:require [buildviz.data.build-schema :refer [build-with-outcome? failed-build?]]
            [buildviz.util.math :as math]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]))

(defn builds-with-outcome [builds]
  (filter build-with-outcome? builds))


(defn builds-grouped-by-same-inputs [builds]
  (->> builds
       (filter :inputs)
       (group-by #(set (:inputs %)))
       vals))

;; flaky builds

(defn- outcomes-for-builds-grouped-by-input [builds]
  (->> builds
       builds-grouped-by-same-inputs
       (map #(map :outcome %))
       (map distinct)))

(defn flaky-build-count [builds]
  (->> (outcomes-for-builds-grouped-by-input builds)
       (map count)
       (filter #(< 1 %))
       count))

;; avg runtime

(defn- runtime-for [{:keys [start end]}]
  (- end start))

(defn- build-runtimes [builds]
  (map runtime-for (filter :end builds)))

(defn average-runtime [builds]
  (when-let [runtimes (seq (build-runtimes builds))]
    (math/avg runtimes)))


(def date-only-formatter (tf/formatter "yyyy-MM-dd" (t/default-time-zone)))

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

(defn- average-by-day [duration-entries]
  (->> duration-entries
       (group-by :name)
       (map (fn [[name duration-entries]]
              [name (average-duration-by-day duration-entries)]))
       (into {})))

(defn job-runtimes-by-day [builds]
  (->> builds
       (filter :end)
       (map (fn [build]
              {:name (:job build)
               :end (:end build)
               :duration (runtime-for build)}))
       average-by-day))

;; error count

(defn fail-count [builds]
  (count (filter failed-build? builds)))
