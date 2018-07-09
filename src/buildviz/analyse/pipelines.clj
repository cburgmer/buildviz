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

(defn- find-build-chains [builds]
  (let [pipeline-end-candidates (->> builds
                                     (filter :triggered-by)
                                     (filter #(is-pipeline-end? % builds)))]
    (->> pipeline-end-candidates
         (mapcat #(find-pipelines-ending-with % builds))
         (filter #(< 1 (count %))))))


(defn- ignore-unsuccessful-pipeline-runs-to-remove-noise-of-interrupted-pipelines [pipeline-runs]
  (remove (fn [pipeline-run]
            (= "fail" (:outcome pipeline-run)))
          pipeline-runs))

(defn- build-chain->pipeline-run [build-chain]
  {:pipeline (map :job build-chain)
   :builds (map #(select-keys % [:job :build-id]) build-chain)
   :start (:start (first build-chain))
   :end (:end (last build-chain))
   :outcome (:outcome (last build-chain))})

(defn pipelines [builds]
  (->> builds
       find-build-chains
       (map build-chain->pipeline-run)
       ignore-unsuccessful-pipeline-runs-to-remove-noise-of-interrupted-pipelines
       (map #(dissoc % :outcome))
       (filter :end)))
