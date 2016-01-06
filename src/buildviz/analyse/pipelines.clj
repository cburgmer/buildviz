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
    (if (:triggered-by (first pipeline))
      (recur (cons (find-build (:triggered-by (first pipeline)) builds)
                   pipeline))
      pipeline)))

(defn find-pipelines [builds]
  (let [pipeline-end-builds (filter #(is-pipeline-end? % builds) builds)]
    (map #(map :job %) (map #(find-pipeline % builds) pipeline-end-builds))))
