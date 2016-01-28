(ns buildviz.analyse.pipelines
  (:require [buildviz.analyse.duration :as duration]
            [buildviz.data.build-schema :refer [was-triggered-by?]]))

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

(defn- for-now-lets-ignore-a-build-can-be-triggered-by-multiple-builds [triggered-by]
  (first triggered-by))

(defn- find-pipeline-ending-with [pipeline-end-build builds]
  (loop [pipeline [pipeline-end-build]]
    (let [current-start (first pipeline)]
      (if-let [triggering-build (find-build (for-now-lets-ignore-a-build-can-be-triggered-by-multiple-builds (:triggered-by current-start))
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
       duration/average-by-day))
