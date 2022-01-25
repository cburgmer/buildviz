(ns buildviz.data.build-facts-schema-test
  (:require [buildviz.data.build-facts-schema :as schema]
            [clojure.test :refer :all]))

(deftest test-validation-errors
  (is (empty? (schema/validation-errors {:jobName "my job" :buildId "42" :start 1})))
  (is (= "#: required key [jobName] not found"
         (first (schema/validation-errors {:buildId "42" :start 1})))))
