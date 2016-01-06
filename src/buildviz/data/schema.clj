(ns buildviz.data.schema
  (:require [closchema.core :as schema]))

(defn- build-schema [start-value]
  {:type "object"
   :properties {:start {:type "integer"
                        :minimum 0}
                :end {:type "integer"
                      :minimum (or start-value
                                   0)}
                :outcome {:enum ["pass" "fail"]}
                :inputs {:type "array"
                         :items {:type "object"
                                 :properties {:revision {:type ["string" "integer"] :required true}
                                              :source_id {:type ["string" "integer"]}
                                              :source-id {:type ["string" "integer"]}}
                                 :additionalProperties false}}
                :triggered-by {:type "object"
                               :properties {:job-name {:type ["string"] :required true}
                                            :build-id {:type ["string" "integer"] :required true}}
                               ;; :required [:jobName :buildId] # Not correctly implemented in closchema
                               :additionalProperties false}}
   :required [:start]
   :additionalProperties false})

(defn build-data-validation-errors [build-data]
  (let [start (get build-data :start)]
    (schema/report-errors (schema/validate (build-schema start) build-data))))


(defn build-with-outcome? [build]
  (contains? build :outcome))

(defn passed-build? [build]
  (= "pass" (:outcome build)))

(defn failed-build? [build]
  (and (build-with-outcome? build)
       (not (passed-build? build))))
