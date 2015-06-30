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
                         :status :error}]}]
           (testsuites-for "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\"><error/></testcase></testsuite></testsuites>")))
    (is (= [{:name "a suite"
             :children [{:name "a test"
                         :status :pass}]}]
           (testsuites-for "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\"></testcase></testsuite></testsuites>")))
    (is (= [{:name "a suite"
             :children [{:name "a test"
                         :classname "the class"
                         :status :fail}]}]
       (testsuites-for "<testsuites><testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"><failure/></testcase></testsuite></testsuites>")))
    (is (= [{:name "a suite"
             :children [{:name "a sub suite"
                         :children [{:name "a test"
                                     :status :pass}]}]}]
           (testsuites-for "<testsuites><testsuite name=\"a suite\"><testsuite name=\"a sub suite\"><testcase name=\"a test\"></testcase></testsuite></testsuite></testsuites>")))
    (is (= [{:name "a suite"
             :children [{:name "a test"
                         :status :pass}]}
             {:name "another suite"
              :children [{:name "another test"
                          :status :pass}]}]
           (testsuites-for "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\"></testcase></testsuite><testsuite name=\"another suite\"><testcase name=\"another test\"></testcase></testsuite></testsuites>")))
    (is (= [{:name "a suite"
             :children [{:name "a test"
                         :status :pass}]}]
           (testsuites-for "<testsuite name=\"a suite\"><testcase name=\"a test\"></testcase></testsuite>")))
    (is (= [{:name "a suite"
             :children [{:name "a test"
                         :status :pass
                         :runtime 1234}]}]
           (testsuites-for "<testsuite name=\"a suite\"><testcase name=\"a test\" time=\"1.234\"></testcase></testsuite>")))
    ))


(defn- a-testcase
  ([name] {:name name})
  ([name value] (merge (a-testcase name)
                       (if (contains? #{:pass :fail} value)
                         {:status value}
                         {:runtime value})))
  ([classname name value] (merge (a-testcase name value)
                             {:classname classname})))

(defn- a-testsuite [name & children]
  {:name name
   :children children})

(deftest TestSuiteAccumulation
  (testing "accumulate-testsuite-failures"
    (is (= []
           (accumulate-testsuite-failures [])))
    (is (= []
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :pass))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :error))]])))
    (is (= [{:name "suite"
             :children [{:name "the class"
                         :children [{:name "a case" :failedCount 1}]}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "the class" "a case" :error))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 1}
                        {:name "another case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))]
                                           [(a-testsuite "suite" (a-testcase "another case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 1}]}
            {:name "another suite"
             :children [{:name "another case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))]
                                           [(a-testsuite "another suite" (a-testcase "another case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 2}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))]
                                           [(a-testsuite "suite" (a-testcase "a case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :pass))]
                                           [(a-testsuite "suite" (a-testcase "a case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "nested suite"
                         :children [{:name "a case" :failedCount 1}
                                    {:name "another case" :failedCount 1}]}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite"
                                                         (a-testsuite "nested suite" (a-testcase "a case" :fail)))]
                                           [(a-testsuite "suite"
                                                         (a-testsuite "nested suite" (a-testcase "another case" :fail)))]])))

    (testing "average-testsuite-runtime"
      (is (= []
             (average-testsuite-runtime [])))
      (is (= [{:name "suite"
               :children [{:name "a case"}]}]
             (average-testsuite-runtime [[(a-testsuite "suite" (a-testcase "a case" :fail))]])))
      (is (= [{:name "suite"
               :children [{:name "a case" :averageRuntime 42}]}]
             (average-testsuite-runtime [[(a-testsuite "suite" (a-testcase "a case" 42))]])))
      (is (= [{:name "suite"
               :children [{:name "a class"
                           :children [{:name "the case" :averageRuntime 42}]}]}]
             (average-testsuite-runtime [[(a-testsuite "suite" (a-testcase "a class" "the case" 42))]])))
      (is (= [{:name "suite"
               :children [{:name "a case" :averageRuntime 20}]}]
             (average-testsuite-runtime [[(a-testsuite "suite" (a-testcase "a case" 30))]
                                         [(a-testsuite "suite" (a-testcase "a case" 10))]])))
      ;; should deal with fractions and round up
      (is (= [{:name "suite"
               :children [{:name "a case" :averageRuntime 21}]}]
             (average-testsuite-runtime [[(a-testsuite "suite" (a-testcase "a case" 30))]
                                         [(a-testsuite "suite" (a-testcase "a case" 11))]])))
      (is (= [{:name "suite"
               :children [{:name "a case" :averageRuntime 10}]}
              {:name "another suite"
               :children [{:name "another case" :averageRuntime 20}]}]
             (average-testsuite-runtime [[(a-testsuite "suite" (a-testcase "a case" 10))]
                                         [(a-testsuite "another suite" (a-testcase "another case" 20))]])))
      (is (= [{:name "suite"
               :children [{:name "nested suite"
                           :children [{:name "a case" :averageRuntime 10}
                                      {:name "another case" :averageRuntime 20}]}]}]
             (average-testsuite-runtime [[(a-testsuite "suite"
                                                       (a-testsuite "nested suite" (a-testcase "a case" 10)))]
                                         [(a-testsuite "suite"
                                                       (a-testsuite "nested suite" (a-testcase "another case" 20)))]]))))

    (testing "average-testsuite-runtime-as-list"
      (is (= []
             (average-testsuite-runtime-as-list [])))
      (is (= [{:testsuite ["suite"] :classname "a class" :name "a case" :averageRuntime 42}]
             (average-testsuite-runtime-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" 42))]])))
      (is (= [{:testsuite ["suite"] :classname "a class" :name "a case" :averageRuntime 20}]
             (average-testsuite-runtime-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" 30))]
                                                 [(a-testsuite "suite" (a-testcase "a class" "a case" 10))]])))
      (is (= [{:testsuite ["another suite"] :classname "another class" :name "another case" :averageRuntime 20}
              {:testsuite ["suite"] :classname "a class" :name "a case" :averageRuntime 10}]
             (average-testsuite-runtime-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" 10))]
                                                 [(a-testsuite "another suite" (a-testcase "another class" "another case" 20))]])))
      (is (= [{:testsuite ["suite" "nested suite"] :classname "another class" :name "another case" :averageRuntime 20}
              {:testsuite ["suite" "nested suite"] :classname "a class" :name "a case" :averageRuntime 10}]
             (average-testsuite-runtime-as-list [[(a-testsuite "suite"
                                                               (a-testsuite "nested suite" (a-testcase "a class" "a case" 10)))]
                                                 [(a-testsuite "suite"
                                                               (a-testsuite "nested suite" (a-testcase "another class" "another case" 20)))]]))))))
