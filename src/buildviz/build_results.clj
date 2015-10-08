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

  (chronological-tests [this job-name from])
  (tests      [this job-name build-id])
  (set-tests! [this job-name build-id xml]))

(defn- builds-starting-from [from builds]
  (if (some? from)
    (->> builds
         (remove (fn [[build-id build]] (nil? (:start build))))
         (filter (fn [[build-id build]] (<= from (:start build)))))
    builds))

(defrecord BuildResults [builds load-tests store-build! store-tests!]
  BuildResultsProtocol

  (job-names [_]
    (keys @builds))

  (builds [_ job-name]
    (when-some [builds (get @builds job-name)]
      (vals builds)))

  (build [_ job-name build-id]
    (get-in @builds [job-name build-id]))

  (set-build! [_ job-name build-id build-data]
    (store-build! job-name build-id build-data)
    (swap! builds assoc-in [job-name build-id] build-data))

  ;; TODO find a solution for 'stale' tests with no matching builds
  (chronological-tests [this job-name from]
    (->> job-name
         (get @(:builds this))
         (builds-starting-from from)
         keys
         (map #(load-tests job-name %))
         (remove nil?)
         seq))

  (tests [_ job-name build-id]
    (load-tests job-name build-id))

  (set-tests! [_ job-name build-id xml]
    (store-tests! job-name build-id xml)))

(defn build-results [builds load-tests store-build! store-tests!]
  (BuildResults. (atom builds) load-tests store-build! store-tests!))
