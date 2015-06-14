(ns buildviz.testsuites-test
  (:use clojure.test
        buildviz.testsuites))

(deftest TestSuites
  (testing "testsuites-for"
    (is (= [{:name "a suite"
             :children [{:name "a test"
                         :status :fail}]}]
           (testsuites-for "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\"><failure/></testcase></testsuite></testsuites>")))
    (is (= [{:name "a suite"
             :children [{:name "a test"
                         :status :pass}]}]
           (testsuites-for "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\"></testcase></testsuite></testsuites>")))
    (is (= [{:name "a suite"
             :children [{:name "a sub suite"
                         :children [{:name "a test"
                                     :status :pass}]}]}]
           (testsuites-for "<testsuites><testsuite name=\"a suite\"><testsuite name=\"a sub suite\"><testcase name=\"a test\"></testcase></testsuite></testsuite></testsuites>")))
    ))


(defn- a-testcase [name status]
  {:name name
   :status status})

(defn- a-testsuite [name & children]
  {:name name
   :children children})

(deftest TestSuiteAccumulation
  (testing "accumulate-testsuite-failures"
    (is (= []
           (accumulate-testsuite-failures [])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "another case" :failedCount 1}
                        {:name "a case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))]
                                           [(a-testsuite "suite" (a-testcase "another case" :fail))]])))
    (is (= [{:name "another suite"
             :children [{:name "another case" :failedCount 1}]}
            {:name "suite"
             :children [{:name "a case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))]
                                           [(a-testsuite "another suite" (a-testcase "another case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 2}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))]
                                           [(a-testsuite "suite" (a-testcase "a case" :fail))]])))
    ))
