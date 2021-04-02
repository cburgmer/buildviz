(ns buildviz.data.build-facts-schema
  (:require [json-schema.core :as schema])
  (:import clojure.lang.ExceptionInfo))

(def build-schema
  {:$schema "http://json-schema.org/draft-07/schema"
   :type "object"
   :required [:job-name :build-id :start]
   :properties {:job-name {:type "string"}
                :build-id {:type "string"}
                :start {:type "integer" :minimum 0}
                :end {:type "integer" :minimum 0}
                :outcome {:type "string" :enum ["pass" "fail"]}
                :inputs {:type "array"
                         :items {:type "object"
                                 :required [:source-id :revision]
                                 :properties {:source-id {:type "string"}
                                              :revision {:type "string"}}
                                 :additionalProperties false}}
                :triggered-by {:type "array"
                              :items {:type "object"
                                      :required [:job-name :build-id]
                                      :properties {:job-name {:type "string"}
                                                   :build-id {:type "string"}}
                                      :additionalProperties false}}
                :test-results {:type "array"
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
