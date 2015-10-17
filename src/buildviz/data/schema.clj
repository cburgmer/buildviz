(ns buildviz.data.schema
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

(defn passed-build? [build]
  (= "pass" (:outcome build)))

(defn failed-build? [build]
  (and (:outcome build)
       (not (passed-build? build))))
