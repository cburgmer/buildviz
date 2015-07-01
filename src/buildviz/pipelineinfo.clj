(ns buildviz.pipelineinfo)

(defn- update-ongoing-fail-phase [fail-phases ongoing-fail-phase]
  (conj (pop fail-phases) ongoing-fail-phase))

(defn- new-ongoing-phase [start culprit]
  {:start start
   :culprits #{culprit}
   :ongoing-culprits #{culprit}})

(defn- accumulate-fail-phases [fail-phases build]
  (let [outcome (:outcome build)
        end (:end build)
        job (:job build)]
    (if (or (empty? fail-phases)
             (empty? (:ongoing-culprits (last fail-phases))))

      (if (= "fail" outcome)
        (conj fail-phases (new-ongoing-phase end job))
        fail-phases)

      (let [ongoing-fail-phase (last fail-phases)]
        (if (and (= "pass" outcome)
                 (contains? (:ongoing-culprits ongoing-fail-phase) job))
          (let [ongoing-culprits (disj (:ongoing-culprits ongoing-fail-phase) job)]
            (update-ongoing-fail-phase fail-phases
                                  (assoc ongoing-fail-phase
                                         :ongoing-culprits ongoing-culprits
                                         :end end)))

          (if (= "fail" outcome)
            (let [culprits (conj (:culprits ongoing-fail-phase) job)
                  ongoing-culprits (conj (:ongoing-culprits ongoing-fail-phase) job)]
              (update-ongoing-fail-phase fail-phases
                                    (assoc ongoing-fail-phase
                                           :culprits culprits
                                           :ongoing-culprits ongoing-culprits)))
            fail-phases))))))

(defn pipeline-fail-phases [builds]
  (map #(dissoc % :ongoing-culprits)
       (reduce accumulate-fail-phases [] builds)))
