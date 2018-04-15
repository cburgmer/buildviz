(ns buildviz.data.build-schema-test
  (:require [buildviz.data.build-schema :as schema]
            [clojure.test :refer :all]))

(defn- first-pointer [validation]
  (-> validation
      first
      :instance
      :pointer))

(defn- first-missing [validation]
  (-> validation
      first
      :missing))

(deftest test-build-validation-errors
  (testing "should require start time"
    (is (empty? (schema/build-validation-errors {:start 1000000000000})))
    (is (= ["start"]
           (first-missing (schema/build-validation-errors {}))))
    (is (= "/start"
           (first-pointer (schema/build-validation-errors {:start -1}))))
    (is (= "/start"
           (first-pointer (schema/build-validation-errors {:start nil})))))

  (testing "should pass on end time equal to start time"
    (is (empty? (schema/build-validation-errors {:start 1000000000000 :end 1000000000000}))))

  (testing "should do a sanity check on the timestamp and disallow anything shorter than 13 digits"
    (is (= "/start"
           (first-pointer (schema/build-validation-errors {:start 999999999999 :end 1000000000000})))))

  (testing "should fail on end time before start time"
    (is (= "/end"
           (first-pointer (schema/build-validation-errors {:start 1453646247759
                                                           :end 1453646247758})))))

  (testing "should fail on missing revision for inputs"
    (is (= ["revision"]
           (first-missing (schema/build-validation-errors {:start 1453646247759
                                                            :inputs [{:source-id "43"}]})))))

  (testing "should fail on missing source-id for inputs"
    (is (= ["source-id"]
           (first-missing (schema/build-validation-errors {:start 1453646247759
                                                            :inputs [{:revision "abcd"}]})))))

  (testing "should fail on missing job-name for triggered-by"
    (is (= "/triggered-by/0"
           (first-pointer (schema/build-validation-errors {:start 1453646247759
                                                           :triggered-by [{:build-id "42"}]})))))

  (testing "should fail on missing build-id for triggered-by"
    (is (= "/triggered-by/0"
           (first-pointer (schema/build-validation-errors {:start 1453646247759
                                                           :triggered-by [{:job-name "the_job"}]})))))

  (testing "should fail on empty triggered-by list"
    (is (= "/triggered-by"
           (first-pointer (schema/build-validation-errors {:start 1453646247759
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
