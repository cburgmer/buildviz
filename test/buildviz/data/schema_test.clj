(ns buildviz.data.schema-test
  (:require [buildviz.data.schema :as schema]
            [clojure.test :refer :all]))

(deftest test-build-data-validation-errors
  (testing "should pass on 0 end time"
    (is (empty? (schema/build-data-validation-errors {:end 0}))))

  (testing "should fail on negative end time"
    (is (not (empty? (schema/build-data-validation-errors {:end -1})))))

  (testing "should fail on end time before start time"
    (is (not (empty? (schema/build-data-validation-errors {:start 42
                                                            :end 41}))))))
