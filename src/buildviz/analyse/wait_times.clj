(ns buildviz.analyse.wait-times)

(defn- find-build [{:keys [job-name build-id]} builds]
  (first (filter (fn [{that-job-name :job that-build-id :build-id}]
                   (and (= job-name that-job-name)
                        (= build-id that-build-id)))
                 builds)))

(defn- build-wait-time [{:keys [job build-id start]} triggering-build]
  (when-let [triggering-build-end (:end triggering-build)]
    (let [wait-time (- start
                       triggering-build-end)]
      {:job job
       :build-id build-id
       :start start
       :wait-time wait-time})))

(defn- longest-build-wait-time [build all-builds]
  (->> (:triggered-by build)
       (map #(find-build % all-builds))
       (map #(build-wait-time build %))
       (apply max-key :wait-time)))

(defn wait-times [builds]
  (->> builds
       (filter :triggered-by)
       (map #(longest-build-wait-time % builds))
       (remove nil?)))
