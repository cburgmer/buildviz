(ns buildviz.analyse.wait-times
  (:require [buildviz.analyse.duration :as duration]))

(defn- find-build [{:keys [job-name build-id]} builds]
  (first (filter (fn [{that-job-name :job that-build-id :build-id}]
                   (and (= job-name that-job-name)
                        (= build-id that-build-id)))
                 builds)))

(defn- build-wait-time [{:keys [job start end]} triggering-build]
  (when-let [triggering-build-end (:end triggering-build)]
    (let [wait-time (- start
                       triggering-build-end)]
      {:name job
       :end end
       :duration wait-time})))

(defn- build-wait-times [build all-builds]
  (->> (:triggered-by build)
       (map #(find-build % all-builds))
       (map #(build-wait-time build %))))

(defn- wait-times [builds]
  (->> builds
       (filter :triggered-by)
       (filter :end)
       (mapcat #(build-wait-times % builds))
       (remove nil?)))


(defn wait-times-by-day [builds]
  (->> builds
       wait-times
       (duration/average-by-day)))
