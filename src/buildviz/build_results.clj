(ns buildviz.build-results
  (:require [closchema.core :as schema]))

(defn- build-schema [start-value]
  (let [minimum-end (if (some? start-value)
                     start-value
                     0)]
    {:type "object"
     :properties {:start {:type "integer"
                          :minimum 0}
                  :end {:type "integer"
                        :minimum minimum-end}
                  :outcome {:enum ["pass" "fail"]}
                  :inputs {:type "array"
                           :items {:type "object"
                                   :properties {:revision {:type ["string" "integer"]}
                                                :source_id {:type ["string" "integer"]}}
                                   :additionalProperties false}}}
     :additionalProperties false}))

(defn build-data-validation-errors [build-data]
  (let [start (get build-data :start)]
    (schema/report-errors (schema/validate (build-schema start) build-data))))


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
