(ns buildviz.analyse.wait-times
  (:require [buildviz.analyse.duration :as duration]))

(defn- for-now-lets-ignore-a-build-can-be-triggered-by-multiple-builds [triggered-by]
  (first triggered-by))

(defn- find-build [{:keys [job-name build-id]} builds]
  (when job-name
    (first (filter (fn [{that-job-name :job that-build-id :build-id}]
                     (and (= job-name that-job-name)
                          (= build-id that-build-id)))
                   builds))))

(defn- build-wait-time [{:keys [job start end triggered-by]} builds]
  (when-let [triggering-build-end (:end (find-build (for-now-lets-ignore-a-build-can-be-triggered-by-multiple-builds triggered-by) builds))]
    (let [wait-time (- start
                       triggering-build-end)]
      {:name job
       :end end
       :duration wait-time})))

(defn- wait-times [builds]
  (->> builds
       (filter :triggered-by)
       (filter :end)
       (map #(build-wait-time % builds))
       (remove nil?)))


(defn wait-times-by-day [builds]
  (->> builds
       wait-times
       (duration/average-by-day)))
