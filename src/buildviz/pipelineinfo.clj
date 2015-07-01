(ns buildviz.pipelineinfo)

(defn- update-ongoing-phase [fail-phases ongoing-phase]
  (conj (pop fail-phases) ongoing-phase))

(defn- accumulate-fail-phases [fail-phases build]
  (let [outcome (:outcome build)
        start (:start build)
        job (:job build)]
    (if (and (not (empty? fail-phases))
             (not (contains? (last fail-phases) :end)))
      (let [ongoing-phase (last fail-phases)]
        (if (and (= "pass" outcome)
                 (contains? (:ongoing-culprits ongoing-phase) job))
          (let [ongoing-culprits (disj (:ongoing-culprits ongoing-phase) job)]
            (if (empty? ongoing-culprits)
              (update-ongoing-phase fail-phases
                                    (assoc ongoing-phase
                                           :end start
                                           :ongoing-culprits ongoing-culprits))
              (update-ongoing-phase fail-phases
                                    (assoc ongoing-phase
                                           :ongoing-culprits ongoing-culprits))))
          (if (= "fail" outcome)
            (let [culprits (conj (:culprits ongoing-phase) job)
                  ongoing-culprits (conj (:ongoing-culprits ongoing-phase) job)]
              (update-ongoing-phase fail-phases
                                    (assoc ongoing-phase
                                           :culprits culprits
                                           :ongoing-culprits ongoing-culprits)))
            fail-phases)))
      (if (= "fail" outcome)
        (conj fail-phases {:start start
                           :culprits #{job}
                           :ongoing-culprits #{job}})
        fail-phases))))

(defn pipeline-fail-phases [builds]
  (map #(dissoc % :ongoing-culprits)
       (reduce accumulate-fail-phases [] builds)))
