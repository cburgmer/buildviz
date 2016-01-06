(ns buildviz.analyse.pipelines
  (:require [buildviz.data.build-schema :refer [was-triggered-by?]]
            [buildviz.util.math :refer [avg]]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]))

(defn- find-build [{:keys [job-name build-id]} builds]
  (when job-name
    (first (filter (fn [{that-job-name :job that-build-id :build-id}]
                     (and (= job-name that-job-name)
                          (= build-id that-build-id)))
                   builds))))

(defn- is-pipeline-end? [candidate-build builds]
  (->> builds
       (filter #(was-triggered-by? % candidate-build))
       empty?))

(defn- find-pipeline-ending-with [pipeline-end-build builds]
  (loop [pipeline [pipeline-end-build]]
    (let [current-start (first pipeline)]
      (if-let [triggering-build (find-build (:triggered-by current-start)
                                            builds)]
        (recur (cons triggering-build pipeline))
        pipeline))))

(defn find-pipeline-runs [builds]
  (let [pipeline-end-candidates (filter #(is-pipeline-end? % builds) builds)]
    (->> pipeline-end-candidates
         (map #(find-pipeline-ending-with % builds))
         (filter #(< 1 (count %))))))


(def date-only-formatter (tf/formatter "yyyy-MM-dd" (t/default-time-zone)))

(defn- date-for [timestamp]
  (when timestamp
    (tf/unparse date-only-formatter (tc/from-long (long timestamp)))))

(defn- pipeline-run-end [builds-of-pipeline-run]
  (:end (last builds-of-pipeline-run)))

(defn- total-runtime [builds-of-pipeline-run]
  (when-let [end (pipeline-run-end builds-of-pipeline-run)]
    (let [start (:start (first builds-of-pipeline-run))]
      (- end start))))

(defn- total-pipeline-runtime-by-day [pipeline-runs]
  (->> pipeline-runs
       (map (fn [builds-of-pipeline-run]
              [(date-for (pipeline-run-end builds-of-pipeline-run))
               (total-runtime builds-of-pipeline-run)]))
       (filter first)
       (group-by first)
       (map (fn [[k pipeline-runtimes]]
              [k (->> pipeline-runtimes
                      (map second)
                      avg)]))
       (into {})))

(defn pipeline-runtimes-by-day [builds]
  (->> builds
       find-pipeline-runs
       (group-by #(map :job %))
       (map (fn [[k pipeline-runs]]
              [k (total-pipeline-runtime-by-day pipeline-runs)]))
       (into {})))
