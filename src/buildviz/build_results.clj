(ns buildviz.build-results)

(def build-schema {:type "object"
                   :properties {:start {:type "integer"}
                                :end {:type "integer"}
                                :outcome {:enum ["pass" "fail"]}
                                :inputs {:type "array"
                                         :items {:type "object"}}}
                   :additionalProperties false})


(defprotocol BuildResultsProtocol
  (job-names [this])
  (builds    [this job-name])
  (build     [this job-name build-id])
  (set-build! [_ job-name build-id build]))

(defrecord BuildResults [builds]
  BuildResultsProtocol

  (job-names [_]
    (keys @builds))

  (builds [_ job-name]
    (when-some [builds (get @builds job-name)]
      (vals builds)))

  (build [_ job-name build-id]
    (get-in @builds [job-name build-id]))

  (set-build! [_ job-name build-id build-data]
    (swap! builds assoc-in [job-name build-id] build-data)))


(defn build-results [builds]
  (BuildResults. builds))
