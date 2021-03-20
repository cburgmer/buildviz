(ns buildviz.data.build-schema-test
  (:require [buildviz.data.build-schema :as schema]
            [clojure.test :refer :all]))

(deftest test-build-validation-errors
  (testing "should require start time"
    (is (empty? (schema/build-validation-errors {:start 1000000000000})))
    (is (= "#: required key [start] not found"
           (first (schema/build-validation-errors {}))))
    (is (= "#/start: -1 is not greater or equal to 1.0E12"
           (first (schema/build-validation-errors {:start -1}))))
    (is (= "#/start: expected type: Number, found: Null"
           (first (schema/build-validation-errors {:start nil})))))

  (testing "should pass on end time equal to start time"
    (is (empty? (schema/build-validation-errors {:start 1000000000000 :end 1000000000000}))))

  (testing "should do a sanity check on the timestamp and disallow anything shorter than 13 digits"
    (is (= "#/start: 999999999999 is not greater or equal to 1.0E12"
           (first (schema/build-validation-errors {:start 999999999999 :end 1000000000000})))))

  (testing "should fail on end time before start time"
    (is (= "#/end: 1453646247758 is not greater or equal to 1453646247759"
           (first (schema/build-validation-errors {:start 1453646247759
                                                   :end 1453646247758})))))

  (testing "should fail on missing revision for inputs"
    (is (= "#/inputs/0: required key [revision] not found"
           (first (schema/build-validation-errors {:start 1453646247759
                                                   :inputs [{:source-id "43"}]})))))

  (testing "should fail on missing source-id for inputs"
    (is (= "#/inputs/0: required key [source-id] not found"
           (first (schema/build-validation-errors {:start 1453646247759
                                                   :inputs [{:revision "abcd"}]})))))

  (testing "should fail on missing job-name for triggered-by"
    (is (= "#/triggered-by/0: required key [job-name] not found"
           (first (schema/build-validation-errors {:start 1453646247759
                                                   :triggered-by [{:build-id "42"}]})))))

  (testing "should fail on missing build-id for triggered-by"
    (is (= "#/triggered-by/0: required key [build-id] not found"
           (first (schema/build-validation-errors {:start 1453646247759
                                                   :triggered-by [{:job-name "the_job"}]})))))

  (testing "should fail on empty triggered-by list"
    (is (= "#/triggered-by: expected minimum item count: 1, found: 0"
           (first (schema/build-validation-errors {:start 1453646247759
                                                   :triggered-by []})))))

  (testing "should allow boolean revision for inputs"
    (is (empty? (schema/build-validation-errors {:start 1453646247759
                                                 :inputs [{:revision true :source-id "something"}]})))))

(deftest test-was-triggered-by?
  (testing "should find triggering build"
    (is (schema/was-triggered-by? {:triggered-by [{:job-name "Test" :build-id "42"}]} {:job "Test" :build-id "42"})))

  (testing "should return false if not triggered by build"
    (is (not (schema/was-triggered-by? {:triggered-by [{:job-name "Test" :build-id 42}]} {:job "Deploy" :build-id 42})))
    (is (not (schema/was-triggered-by? {:triggered-by [{:job-name "Test" :build-id 42}]} {:job "Test" :build-id 43})))))
