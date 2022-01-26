(ns buildviz.data.build-facts-schema
  (:require [json-schema.core :as schema])
  (:import clojure.lang.ExceptionInfo))

(def build-schema
  (slurp (clojure.java.io/resource "schema.json")))

(defn validation-errors [build]
  (try
    (schema/validate build-schema build)
    (list)
    (catch ExceptionInfo e
      (:errors (ex-data e)))))
