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

(defrecord BuildResults [builds load-tests]
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

  (has-tests? [this job-name]
    (some? (chronological-tests this job-name)))

  ;; TODO find a solution for 'stale' tests with no matching builds
  (chronological-tests [this job-name]
    (->> job-name
         (get @(:builds this))
         keys
         (map #(load-tests job-name %))
         (remove nil?)
         seq))

  (tests [_ job-name build-id]
    (load-tests job-name build-id))

  (set-tests! [_ job-name build-id xml]
    nil))

(defn build-results [builds load-tests]
  (BuildResults. (atom builds) load-tests))
