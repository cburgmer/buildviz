(ns buildviz.pipelineinfo)

(defn- update-ongoing-fail-phase [fail-phases ongoing-fail-phase]
  (conj (pop fail-phases) ongoing-fail-phase))

(defn- new-ongoing-phase [start culprit]
  {:start start
   :culprits #{culprit}
   :ongoing-culprits #{culprit}})

(defn- open-new-fail-phase [fail-phases {job :job end :end}]
  (conj fail-phases (new-ongoing-phase end job)))

(defn- add-failing-build-to-fail-phase [fail-phases ongoing-fail-phase {job :job}]
  (let [culprits (conj (:culprits ongoing-fail-phase) job)
        ongoing-culprits (conj (:ongoing-culprits ongoing-fail-phase) job)]
    (update-ongoing-fail-phase fail-phases
                               (assoc ongoing-fail-phase
                                      :culprits culprits
                                      :ongoing-culprits ongoing-culprits))))

(defn- pop-passing-build-from-fail-phase [fail-phases ongoing-fail-phase {job :job end :end}]
  (let [ongoing-culprits (disj (:ongoing-culprits ongoing-fail-phase) job)]
    (update-ongoing-fail-phase fail-phases
                               (assoc ongoing-fail-phase
                                      :ongoing-culprits ongoing-culprits
                                      :end end))))

(defn- accumulate-fail-phases [fail-phases build]
  (let [outcome (:outcome build)
        job (:job build)]
    (if (or (empty? fail-phases)
            (empty? (:ongoing-culprits (last fail-phases))))

      (if (= outcome "fail")
        (open-new-fail-phase fail-phases build)
        fail-phases)

      (let [ongoing-fail-phase (last fail-phases)]
        (if (and (= outcome "pass")
                 (contains? (:ongoing-culprits ongoing-fail-phase) job))
          (pop-passing-build-from-fail-phase fail-phases ongoing-fail-phase build)

          (if (= outcome "fail")
            (add-failing-build-to-fail-phase fail-phases ongoing-fail-phase build)
            fail-phases))))))

(defn pipeline-fail-phases [builds]
  (map #(dissoc % :ongoing-culprits)
       (reduce accumulate-fail-phases [] builds)))
