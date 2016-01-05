(ns buildviz.data.schema-test
  (:require [buildviz.data.schema :as schema]
            [clojure.test :refer :all]))

(deftest test-build-data-validation-errors
  (testing "should require start time"
    (is (empty? (schema/build-data-validation-errors {:start 0})))
    (is (= [:start]
           (:path (first (schema/build-data-validation-errors {})))))
    (is (= [:start]
           (:path (first (schema/build-data-validation-errors {:start -1})))))
    (comment
      "https://github.com/bigmlcom/closchema/pull/35"
      (is (= [:start]
           (:path (first (schema/build-data-validation-errors {:start nil})))))))

  (testing "should pass on 0 end time"
    (is (empty? (schema/build-data-validation-errors {:start 0 :end 0}))))

  (testing "should fail on negative end time"
    (is (= [:end]
           (:path (first (schema/build-data-validation-errors {:end -1}))))))

  (testing "should fail on end time before start time"
    (is (= [:end]
           (:path (first (schema/build-data-validation-errors {:start 42
                                                               :end 41}))))))

  (testing "should fail on missing jobName for triggeredBy"
    (is (= [:triggeredBy :jobName]
           (:path (first (schema/build-data-validation-errors {:start 42
                                                               :triggeredBy {:buildId 42}}))))))

  (testing "should fail on missing buildId for triggeredBy"
    (is (= [:triggeredBy :buildId]
           (:path (first (schema/build-data-validation-errors {:start 42
                                                               :triggeredBy {:jobName "the_job"}}))))))
  )
