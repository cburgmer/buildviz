(ns buildviz.analyse.wait-times)

(defn- find-build [{:keys [job-name build-id]} builds]
  (first (filter (fn [{that-job-name :job that-build-id :build-id}]
                   (and (= job-name that-job-name)
                        (= build-id that-build-id)))
                 builds)))

(defn- build-wait-time [{:keys [job build-id start]} triggering-build]
  (let [wait-time (- start
                     (:end triggering-build))]
    {:job job
     :build-id build-id
     :start start
     :wait-time wait-time
     :triggered-by (select-keys triggering-build [:job :build-id])}))

;; Jenkins for example may report multiple builds of the same jobs as trigger,
;; if the triggered job is slow to schedule, and multiple triggering builds
;; have happened in the meantime. As of wait times, the oldest should obviously
;; count as it's been waiting the longest.
(defn- earliest-triggering-candidates [triggering-builds]
  (->> triggering-builds
       (group-by :job)
       (map (fn [[_ builds]] (apply min-key :end builds)))))

(defn- longest-build-wait-time [build all-builds]
  (when-let [triggered-builds (->> (:triggered-by build)
                                   (map #(find-build % all-builds))
                                   (remove nil?)
                                   (filter :end)
                                   seq)]
    ;; If a job needs multiple preceding jobs to be triggered, then only the
    ;; latest will finally fulfill the requirement for a successful trigger,
    ;; hence the wait time will start there.
    (let [latest-triggering-build (->> triggered-builds
                                       earliest-triggering-candidates
                                       (apply max-key :end))]
      (build-wait-time build latest-triggering-build))))

(defn wait-times [builds]
  (->> builds
       (filter :triggered-by)
       (map #(longest-build-wait-time % builds))
       (remove nil?)))
