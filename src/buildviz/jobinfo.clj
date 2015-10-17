(ns buildviz.jobinfo
  (:require [buildviz.data.schema :as schema]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]))

(defn builds-with-outcome [build-data-entries]
  (filter #(contains? % :outcome) build-data-entries))


(defn- build-inputs-as-map [build]
  (let [inputs (:inputs build)
        input-map (->> inputs
                       (map (fn [{id :id revision :revision}] {id revision}))
                       (into {}))]
    (assoc build :inputs input-map)))

(defn builds-grouped-by-same-inputs [builds]
  (->> builds
       (filter :inputs)
       (map build-inputs-as-map)
       (group-by :inputs)
       vals))

;; flaky builds

(defn- outcomes-for-builds-grouped-by-input [build-data-entries]
  (->> build-data-entries
       builds-grouped-by-same-inputs
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
  (filter some?
          (map runtime-for build-data-entries)))

(defn average-runtime [build-data-entries]
  (if-let [runtimes (seq (build-runtime build-data-entries))]
    (avg runtimes)))


(def date-only-formatter (tf/formatter "yyyy-MM-dd" (t/default-time-zone)))

(defn- date-for [{end :end}]
  (when (some? end)
    (tf/unparse date-only-formatter (tc/from-long (long end)))))

(defn average-runtime-by-day [build-data-entries]
  (->> (group-by date-for build-data-entries)
       (map (fn [[date builds]] [date (average-runtime builds)]))
       (filter second)
       (into {})))

;; error count

(defn fail-count [build-data-entries]
  (count (filter schema/failed-build? build-data-entries)))
