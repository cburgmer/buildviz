(ns buildviz.analyse.pipelines
  (:require [buildviz.data.build-schema :refer [was-triggered-by?]]))

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

(defn- triggering-builds [{triggered-by :triggered-by} builds]
  (->> triggered-by
       (map #(find-build % builds))
       (remove nil?)))

(defn- recur-pipeline-ending-with [pipeline builds]
  (let [current-start (first pipeline)]
    (if (contains? current-start :triggered-by)
      (->> (triggering-builds current-start builds)
           (map #(cons % pipeline))
           (mapcat #(recur-pipeline-ending-with % builds)))
      [pipeline])))

(defn- find-pipelines-ending-with [pipeline-end-build builds]
  (let [pipeline [pipeline-end-build]]
    (recur-pipeline-ending-with pipeline builds)))

(defn- find-pipeline-runs [builds]
  (let [pipeline-end-candidates (->> builds
                                     (filter :triggered-by)
                                     (filter #(is-pipeline-end? % builds)))]
    (->> pipeline-end-candidates
         (mapcat #(find-pipelines-ending-with % builds))
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

(defn- build-pipeline-run [pipeline-run]
  {:pipeline (pipeline-run-name pipeline-run)
   :start (pipeline-run-start pipeline-run)
   :end (pipeline-run-end pipeline-run)})

(defn pipelines [builds]
  (->> builds
       find-pipeline-runs
       ignore-unsuccessful-pipeline-runs-to-remove-noise-of-interrupted-pipelines
       (filter pipeline-run-end)
       (map build-pipeline-run)
       (map #(dissoc % :name :duration))))
