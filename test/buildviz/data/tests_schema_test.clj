(ns buildviz.data.tests-schema-test
  (:require [buildviz.data.tests-schema :as tests-schema]
            [clojure.test :refer :all]))

(defn- a-suite [children]
  {:name "Suite"
   :children children})

(defn- a-testcase
  ([]
   (a-testcase {}))
  ([testcase]
   (merge {:name "Test"
           :classname "Class"
           :runtime 101
           :status "pass"}
          testcase)))

(deftest test-tests-validation-errors
  (testing "valid input"
    (is (empty? (tests-schema/tests-validation-errors [])))
    (is (empty? (tests-schema/tests-validation-errors [{:name "Suite"
                                                        :children []}])))
    (is (empty? (tests-schema/tests-validation-errors [(a-suite [{:name "Test"
                                                                  :classname "Class"
                                                                  :runtime 101
                                                                  :status "pass"}])])))
    (is (empty? (tests-schema/tests-validation-errors [(a-suite [{:name "Test"
                                                                  :runtime 101
                                                                  :status "pass"}])])))
    (is (empty? (tests-schema/tests-validation-errors [(a-suite [(a-testcase {:status "fail"})])])))
    (is (empty? (tests-schema/tests-validation-errors [(a-suite [(a-testcase {:status "error"})])])))
    (is (empty? (tests-schema/tests-validation-errors [(a-suite [(a-testcase {:status "skipped"})])]))))

  (testing "invalid input"
    (is (not (empty? (tests-schema/tests-validation-errors [(a-suite [(dissoc (a-testcase) :name)])]))))
    (is (not (empty? (tests-schema/tests-validation-errors [(a-suite [(dissoc (a-testcase) :status)])]))))
    (is (not (empty? (tests-schema/tests-validation-errors [(a-suite [(dissoc (a-testcase) :runtime)])]))))
    (is (not (empty? (tests-schema/tests-validation-errors [(a-suite [(a-testcase {:status "failure"})])]))))
    (is (not (empty? (tests-schema/tests-validation-errors [(a-suite [(a-testcase {:runtime "101"})])])))))
  )
