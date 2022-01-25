(ns buildviz.data.build-facts-schema
  (:require [json-schema.core :as schema])
  (:import clojure.lang.ExceptionInfo))

(def build-schema
  {:$schema "http://json-schema.org/draft-07/schema"
   :type "object"
   :required [:jobName :buildId :start]
   :properties {:jobName {:type "string"}
                :buildId {:type "string"}
                :start {:type "integer" :minimum 0}
                :end {:type "integer" :minimum 0}
                :outcome {:type "string" :enum ["pass" "fail"]}
                :inputs {:type "array"
                         :items {:type "object"
                                 :required [:sourceId :revision]
                                 :properties {:sourceId {:type "string"}
                                              :revision {:type "string"}}
                                 :additionalProperties false}}
                :triggeredBy {:type "array"
                              :items {:type "object"
                                      :required [:jobName :buildId]
                                      :properties {:jobName {:type "string"}
                                                   :buildId {:type "string"}}
                                      :additionalProperties false}}
                :testResults {:type "array"
                              :items {:type "object"
                                      :required [:name :children]
                                      :properties {:name {:type "string"}
                                                   :children {:type "array"
                                                              :items {:type "object"
                                                                      :required [:classname :name :status]
                                                                      :properties {:classname {:type "string"}
                                                                                   :name {:type "string"}
                                                                                   :runtime {:type "integer" :minimum 0}
                                                                                   :status {:type "string" :enum ["pass" "fail" "skipped" "error"]}}
                                                                      :additionalProperties false}}}
                                      :additionalProperties false}}}
   :additionalProperties false})

(defn validation-errors [build]
  (try
    (schema/validate build-schema build)
    (list)
    (catch ExceptionInfo e
      (:errors (ex-data e)))))
