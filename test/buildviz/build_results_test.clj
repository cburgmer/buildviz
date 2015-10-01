(ns buildviz.build-results-test
  (:use clojure.test)
  (:require [buildviz.build-results :as results]))


(deftest test-build-data-validation-errors
  (testing "should pass on 0 end time"
    (is (empty? (results/build-data-validation-errors {:end 0}))))

  (testing "should fail on negative end time"
    (is (not (empty? (results/build-data-validation-errors {:end -1})))))

  (testing "should fail on end time before start time"
    (is (not (empty? (results/build-data-validation-errors {:start 42
                                                            :end 41}))))))
