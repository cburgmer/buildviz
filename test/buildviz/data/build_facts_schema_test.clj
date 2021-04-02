(ns buildviz.data.build-facts-schema-test
  (:require [buildviz.data.build-facts-schema :as schema]
            [clojure.test :refer :all]))

(deftest test-validation-errors
  (is (empty? (schema/validation-errors {:job-name "my job" :build-id "42" :start 1})))
  (is (= "#: required key [job-name] not found"
         (first (schema/validation-errors {:build-id "42" :start 1})))))
