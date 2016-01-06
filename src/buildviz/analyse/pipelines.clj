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

(defn- find-pipeline-ending-with [pipeline-end-build builds]
  (loop [pipeline [pipeline-end-build]]
    (let [current-start (first pipeline)]
      (if-let [triggering-build (find-build (:triggered-by current-start)
                                            builds)]
        (recur (cons triggering-build pipeline))
        pipeline))))

(defn find-pipelines [builds]
  (let [pipeline-end-candidates (filter #(is-pipeline-end? % builds) builds)]
    (->> pipeline-end-candidates
         (map #(find-pipeline-ending-with % builds))
         (filter #(< 1 (count %)))
         (group-by #(map :job %)))))
