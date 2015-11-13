(ns buildviz.data.schema-test
  (:require [buildviz.data.schema :as schema]
            [clojure.test :refer :all]))

(deftest test-build-data-validation-errors
  (testing "should require start time"
    (is (not (empty? (schema/build-data-validation-errors {}))))
    (is (empty? (schema/build-data-validation-errors {:start 0}))))

  (testing "should pass on 0 end time"
    (is (empty? (schema/build-data-validation-errors {:start 0 :end 0}))))

  (testing "should fail on negative end time"
    (is (= (:path (first (schema/build-data-validation-errors {:end -1})))
           [:end])))

  (testing "should fail on end time before start time"
    (is (= (:path (first (schema/build-data-validation-errors {:start 42
                                                               :end 41})))
           [:end]))))
