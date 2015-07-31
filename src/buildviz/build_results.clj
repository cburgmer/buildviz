(ns buildviz.build-results)

(def build-schema {:type "object"
                   :properties {:start {:type "integer"}
                                :end {:type "integer"}
                                :outcome {:enum ["pass" "fail"]}
                                :inputs {:type "array"
                                         :items {:type "object"}}}
                   :additionalProperties false})


(defprotocol BuildResultsProtocol
  (job-names  [this])
  (builds     [this job-name])
  (build      [this job-name build-id])
  (set-build! [this job-name build-id build])

  (has-tests? [this job-name])
  (chronological-tests [this job-name])
  (tests      [this job-name build-id])
  (set-tests! [this job-name build-id xml]))

(defrecord BuildResults [builds tests]
  BuildResultsProtocol

  (job-names [_]
    (keys @builds))

  (builds [_ job-name]
    (when-some [builds (get @builds job-name)]
      (vals builds)))

  (build [_ job-name build-id]
    (get-in @builds [job-name build-id]))

  (set-build! [_ job-name build-id build-data]
    (swap! builds assoc-in [job-name build-id] build-data))

  (has-tests? [_ job-name]
    (some? (get @tests job-name)))

  (chronological-tests [_ job-name]
    (when-some [test-results (get @tests job-name)]
      (vals test-results)))

  (tests [_ job-name build-id]
    (get-in @tests [job-name build-id]))

  (set-tests! [_ job-name build-id xml]
    (swap! tests assoc-in [job-name build-id] xml)))


(defn build-results [builds tests]
  (BuildResults. (atom builds) (atom tests)))
