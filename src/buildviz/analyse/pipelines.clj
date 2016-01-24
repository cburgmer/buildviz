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

(defn- find-pipeline-runs [builds]
  (let [pipeline-end-candidates (->> builds
                                     (filter :triggered-by)
                                     (filter #(is-pipeline-end? % builds)))]
    (->> pipeline-end-candidates
         (map #(find-pipeline-ending-with % builds))
         (filter #(< 1 (count %))))))


(def ^:private date-only-formatter (tf/formatter "yyyy-MM-dd" (t/default-time-zone)))

(defn- date-for [timestamp]
  (tf/unparse date-only-formatter (tc/from-long (long timestamp))))

(defn- average-duration [builds]
  (avg (map :duration builds)))

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


(defn- pipeline-run-end [builds-of-pipeline-run]
  (:end (last builds-of-pipeline-run)))

(defn- pipeline-run-start [builds-of-pipeline-run]
  (:start (first builds-of-pipeline-run)))

(defn- pipeline-run-outcome [builds-of-pipeline-run]
  (:outcome (last builds-of-pipeline-run)))

(defn- pipeline-run-name [builds-of-pipeline-run]
  (map :job builds-of-pipeline-run))

(defn- ignore-unsuccessful-pipeline-runs-to-remove-noise-of-interrupted-pipelines [pipeline-runs]
  (remove (fn [pipeline-run]
            (= "fail" (pipeline-run-outcome pipeline-run)))
          pipeline-runs))

(defn- pipeline-run->duration [pipeline-run]
  {:name (pipeline-run-name pipeline-run)
   :end (pipeline-run-end pipeline-run)
   :duration (- (pipeline-run-end pipeline-run)
                (pipeline-run-start pipeline-run))})

(defn pipeline-runtimes-by-day [builds]
  (->> builds
       find-pipeline-runs
       ignore-unsuccessful-pipeline-runs-to-remove-noise-of-interrupted-pipelines
       (filter pipeline-run-end)
       (map pipeline-run->duration)
       average-by-day))
