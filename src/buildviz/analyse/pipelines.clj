(ns buildviz.analyse.pipelines)

(defn- find-build [{:keys [job-name build-id]} builds]
  (first (filter (fn [{that-job-name :job that-build-id :build-id}]
                   (and (= job-name that-job-name)
                        (= build-id that-build-id)))
                 builds)))

(defn- was-triggered-by? [{{this-job-name :job-name this-build-id :build-id} :triggered-by}
                          {that-job-name :job that-build-id :build-id}]
  (and (= this-job-name that-job-name)
       (= this-build-id that-build-id)))

(defn- is-pipeline-end? [build builds]
  (empty? (filter #(was-triggered-by? % build)
                  builds)))

(defn- find-pipeline [pipeline-end-build builds]
  (loop [pipeline [pipeline-end-build]]
    (let [current-start (first pipeline)]
      (if (:triggered-by current-start)
        (if-let [triggering-build (find-build (:triggered-by current-start)
                                              builds)]
          (recur (cons triggering-build pipeline))
          pipeline)
        pipeline))))

(defn find-pipelines [builds]
  (let [pipeline-end-builds (filter #(is-pipeline-end? % builds) builds)]
    (->> pipeline-end-builds
         (map #(find-pipeline % builds))
         (filter #(< 1 (count %)))
         (map #(map :job %)))))
