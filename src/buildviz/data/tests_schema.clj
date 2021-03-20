(ns buildviz.data.tests-schema
  (:require [json-schema.core :as schema])
  (:import clojure.lang.ExceptionInfo))

;; TODO The schema does not fully resemble the internal schema, as values for :status are keywords, not strings (:pass not "pass")
(def tests-schema {:$schema "http://json-schema.org/draft-04/schema"
                   :type "array"
                   :items {:type "object"
                           :properties {:name {:type "string"}
                                        :children {:type "array"
                                                   :items {:type "object"
                                                           :properties {:name {:type "string"}
                                                                        :classname {:type "string"}
                                                                        :runtime {:type "integer"}
                                                                        :status {:enum ["pass" "fail" "skipped" "error"]}}
                                                           :required [:name :classname :status]}}}
                           :required [:name :children]}})

(defn tests-validation-errors [test-results]
  (try
    (schema/validate tests-schema (apply vector test-results)) ; https://github.com/luposlip/json-schema/issues/7
    (list)
    (catch ExceptionInfo e
      (:errors (ex-data e)))))


(defn is-ok? [{status :status}]
  (contains? #{:pass :skipped} status))

(defn is-skipped? [{status :status}]
  (= status :skipped))
