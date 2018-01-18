(ns buildviz.data.build-schema
  (:require [closchema.core :as schema]))

(def minimum-13-digit-timestamp (Math/pow 10 12)) ; Sun, 09 Sep 2001 01:46:40 GMT

(defn- build-schema [start-value]
  {:type "object"
   :properties {:start {:type "integer"
                        :minimum minimum-13-digit-timestamp}
                :end {:type "integer"
                      :minimum (or start-value
                                   minimum-13-digit-timestamp)}
                :outcome {:enum ["pass" "fail"]}
                :inputs {:type "array"
                         :items {:type "object"
                                 :properties {:revision {:type ["string" "integer" "boolean"] :required true}
                                              :source-id {:type ["string" "integer"] :required true}}
                                 :additionalProperties false}}
                :triggered-by {:type "array"
                               :minItems 1
                               :items {:type "object"
                                       :properties {:job-name {:type "string" :required true}
                                                    :build-id {:type "string" :required true}}
                                       ;; :required [:jobName :buildId] # Not correctly implemented in closchema
                                       :additionalProperties false}}}
   :required [:start]
   :additionalProperties false})

(defn build-validation-errors [{:keys [start] :as build}]
  (schema/report-errors (schema/validate (build-schema start) build)))


(defn build-with-outcome? [build]
  (contains? build :outcome))

(defn passed-build? [build]
  (= "pass" (:outcome build)))

(defn failed-build? [build]
  (and (build-with-outcome? build)
       (not (passed-build? build))))

(defn was-triggered-by? [{triggered-by :triggered-by}
                         {that-job-name :job that-build-id :build-id}]
  (some (fn [{this-job-name :job-name this-build-id :build-id}]
          (and (= this-job-name that-job-name)
               (= this-build-id that-build-id)))
        triggered-by))
