(ns buildviz.data.tests-schema
  (:require [closchema.core :as schema]))

;; TODO The schema does not fully resemble the internal schema, as values for :status are keywords, not strings (:pass not "pass")
(def tests-schema {:type "array"
                   :items {:type "object"
                           :properties {:name "string"
                                        :children {:type "array"
                                                   :items {:type "object"
                                                           :properties {:name "string"
                                                                        :classname "string"
                                                                        :runtime "integer"
                                                                        :status {:enum ["pass" "fail" "skipped" "error"]}}
                                                           :required [:name :runtime :status]}}}
                           :required [:name :children]}
                   })

(defn tests-validation-errors [test-results]
  (schema/report-errors (schema/validate tests-schema test-results)))


(defn is-ok? [{status :status}]
  (contains? #{:pass :skipped} status))
