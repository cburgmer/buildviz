(ns buildviz.go.aggregate
  (:require [clojure.tools.logging :as log]))

(defn- aggregate-build-times [job-instances]
  (let [start-times (map :start job-instances)
        end-times (map :end job-instances)]
    (if (and (empty? (filter nil? end-times))
             (seq end-times))
      {:start (apply min start-times)
       :end (apply max end-times)}
      {})))

(defn- ignore-old-runs-for-rerun-stages [job-instances stage-run]
  (filter #(= stage-run (:actual-stage-run %)) job-instances))

(defn- aggregate-builds [{:keys [stage-run job-instances]}]
  (let [outcomes (map :outcome job-instances)
        accumulated-outcome (if (every? #(= "pass" %) outcomes)
                              "pass"
                              "fail")]
    (-> job-instances
        (ignore-old-runs-for-rerun-stages stage-run)
        aggregate-build-times
        (assoc :outcome accumulated-outcome))))

(defn aggregate-jobs-for-stage [stage-instance]
  (let [aggregated-junit-xml (->> (:job-instances stage-instance)
                                  (mapcat :junit-xml)
                                  (remove nil?)
                                  seq)
        aggregated-build (aggregate-builds stage-instance)]
    (assoc aggregated-build
           :junit-xml aggregated-junit-xml)))
