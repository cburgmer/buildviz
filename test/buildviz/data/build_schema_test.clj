(ns buildviz.data.build-schema-test
  (:require [buildviz.data.build-schema :as schema]
            [clojure.test :refer :all]))

(deftest test-build-validation-errors
  (testing "should require start time"
    (is (empty? (schema/build-validation-errors {:start 1000000000000})))
    (is (= [:start]
           (:path (first (schema/build-validation-errors {})))))
    (is (= [:start]
           (:path (first (schema/build-validation-errors {:start -1})))))
    (comment
      "https://github.com/bigmlcom/closchema/pull/35"
      (is (= [:start]
             (:path (first (schema/build-validation-errors {:start nil})))))))

  (testing "should pass on end time equal to start time"
    (is (empty? (schema/build-validation-errors {:start 1000000000000 :end 1000000000000}))))

  (testing "should do a sanity check on the timestamp and disallow anything shorter than 13 digits"
    (is (= [:start]
           (:path (first (schema/build-validation-errors {:start 999999999999 :end 1000000000000}))))))

  (testing "should fail on negative end time"
    (is (= [:end]
           (:path (first (schema/build-validation-errors {:end -1}))))))

  (testing "should fail on end time before start time"
    (is (= [:end]
           (:path (first (schema/build-validation-errors {:start 1453646247759
                                                          :end 1453646247758}))))))

  (testing "should fail on missing revision for inputs"
    (is (= [:inputs 0 :revision]
           (:path (first (schema/build-validation-errors {:start 1453646247759
                                                          :inputs [{:source-id "43"}]}))))))

  (testing "should fail on missing sourceId for inputs"
    (is (= [:inputs 0 :source-id]
           (:path (first (schema/build-validation-errors {:start 1453646247759
                                                          :inputs [{:revision "abcd"}]}))))))

  (testing "should fail on missing jobName for triggeredBy"
    (is (= [:triggered-by 0 :job-name]
           (:path (first (schema/build-validation-errors {:start 1453646247759
                                                          :triggered-by [{:build-id "42"}]}))))))

  (testing "should fail on missing buildId for triggeredBy"
    (is (= [:triggered-by 0 :build-id]
           (:path (first (schema/build-validation-errors {:start 1453646247759
                                                          :triggered-by [{:job-name "the_job"}]}))))))

  (testing "should fail on empty triggeredBy list"
    (is (= [:triggered-by]
           (:path (first (schema/build-validation-errors {:start 1453646247759
                                                          :triggered-by []})))))))

(deftest test-was-triggered-by?
  (testing "should find triggering build"
    (is (schema/was-triggered-by? {:triggered-by [{:job-name "Test" :build-id "42"}]} {:job "Test" :build-id "42"})))

  (testing "should return false if not triggered by build"
    (is (not (schema/was-triggered-by? {:triggered-by [{:job-name "Test" :build-id 42}]} {:job "Deploy" :build-id 42})))
    (is (not (schema/was-triggered-by? {:triggered-by [{:job-name "Test" :build-id 42}]} {:job "Test" :build-id 43})))))
