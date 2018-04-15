(ns buildviz.data.tests-schema
  (:require [scjsv.core :as schema]))

;; TODO The schema does not fully resemble the internal schema, as values for :status are keywords, not strings (:pass not "pass")
(def tests-schema {:type "array"
                   :items {:type "object"
                           :properties {:name {:type "string"}
                                        :children {:type "array"
                                                   :items {:type "object"
                                                           :properties {:name {:type "string"}
                                                                        :classname {:type "string"}
                                                                        :runtime {:type "integer"}
                                                                        :status {:enum ["pass" "fail" "skipped" "error"]}}
                                                           :required [:name :classname :status]}}}
                           :required [:name :children]}
                   })

(defn tests-validation-errors [test-results]
  (let [validate (schema/validator tests-schema)]
    (validate test-results)))


(defn is-ok? [{status :status}]
  (contains? #{:pass :skipped} status))

(defn is-skipped? [{status :status}]
  (= status :skipped))
