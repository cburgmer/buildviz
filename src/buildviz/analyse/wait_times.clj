(ns buildviz.analyse.wait-times
  (:require [buildviz.util.math :refer [avg]]
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

(defn- build-wait-time [{:keys [job start end triggered-by]} builds]
  (when-let [triggering-build-end (:end (find-build triggered-by builds))]
    (let [wait-time (- start
                       triggering-build-end)]
      {:job job
       :end end
       :wait-time wait-time})))

(defn- wait-times [builds]
  (->> builds
       (filter :triggered-by)
       (filter :end)
       (map #(build-wait-time % builds))
       (remove nil?)))


(def ^:private date-only-formatter (tf/formatter "yyyy-MM-dd" (t/default-time-zone)))

(defn- date-for [timestamp]
  (when timestamp
    (tf/unparse date-only-formatter (tc/from-long (long timestamp)))))

(defn- average-wait-time [builds]
  (avg (map :wait-time builds)))

(defn- average-by-day [build-wait-times]
  (->> build-wait-times
       (group-by #(date-for (:end %)))
       (map (fn [[date builds]]
              [date (average-wait-time builds)]))
       (into {})))

(defn wait-times-by-day [builds]
  (->> builds
       wait-times
       (group-by :job)
       (map (fn [[job builds]]
              [job (average-by-day builds)]))
       (into {})))
