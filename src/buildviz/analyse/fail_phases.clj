(ns buildviz.analyse.fail-phases
  (:require [buildviz.data.build-schema :as schema]))

(defn- new-phase [build]
  (cond-> {:start (:end build)
           :status (if (schema/failed-build? build) "fail" "pass")}
    (schema/failed-build? build) (assoc :culprits #{(:job build)}
                                        :ongoing-culprits #{(:job build)})))

(defn- update-current-phase [phases current-phase]
  (conj (pop phases) current-phase))

(defn- extend-phase [phases current-phase build]
  (update-current-phase phases (assoc current-phase :end (:end build))))

(defn- close-phase-and-open-new [phases current-phase build]
  (conj (update-current-phase phases (assoc current-phase :end (:end build)))
        (new-phase build)))

(defn- failing-phase-resolved? [phase]
  (empty? (:ongoing-culprits phase)))

(defn- accumulate-phases [phases build]
  (if (empty? phases)
    [(new-phase build)]

    (let [current-phase (last phases)]
      (if (= "pass" (:status current-phase))
        (if (schema/passed-build? build)
          (extend-phase phases current-phase build)
          (close-phase-and-open-new phases current-phase build))

        (if (schema/failed-build? build)
          (extend-phase phases
                        (-> current-phase
                            (update-in [:ongoing-culprits] conj (:job build))
                            (update-in [:culprits] conj (:job build)))
                        build)
          (let [updated-phase (update-in current-phase [:ongoing-culprits] disj (:job build))]
              (if (failing-phase-resolved? updated-phase)
                (close-phase-and-open-new phases updated-phase build)
                (extend-phase phases updated-phase build))))))))

(defn pipeline-phases [builds]
  (->> builds
       (remove #(or (nil? (:end %))
                    (nil? (:outcome %))))
       (reduce accumulate-phases [])
       (remove #(nil? (:end %)))))

;; legacy
(defn pipeline-fail-phases [builds]
  (->> builds
       pipeline-phases
       (filter #(= "fail" (:status %)))
       (map #(dissoc % :ongoing-culprits :status))))
