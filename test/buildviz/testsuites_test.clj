(ns buildviz.testsuites-test
  (:use clojure.test
        buildviz.testsuites))

(defn- a-testcase
  ([name] {:name name})
  ([name value] (merge (a-testcase name)
                       {:status value}))
  ([classname name value] (merge (a-testcase name value)
                                 {:classname classname})))

(defn- a-testcase-with-runtime
  ([name value] {:name name
                 :runtime value})
  ([classname name value] (merge (a-testcase-with-runtime name value)
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
    (is (= []
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :skipped))]])))
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
                                                         (a-testsuite "nested suite" (a-testcase "another case" :fail)))]]))))

  (testing "accumulate-testsuite-failures-as-list"
    (is (= []
           (accumulate-testsuite-failures-as-list [])))
    (is (= []
           (accumulate-testsuite-failures-as-list [[(a-testsuite "suite" (a-testcase "a case" :pass))]])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :failedCount 1}]
           (accumulate-testsuite-failures-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" :fail))]])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :failedCount 1}]
           (accumulate-testsuite-failures-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" :error))]])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "another case"
             :failedCount 1}
            {:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :failedCount 1}]
           (accumulate-testsuite-failures-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" :fail))]
                                                   [(a-testsuite "suite" (a-testcase "a class" "another case" :fail))]])))
    (is (= [{:testsuite ["another suite"]
             :classname "a class"
             :name "another case"
             :failedCount 1}
            {:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :failedCount 1}]
           (accumulate-testsuite-failures-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" :fail))]
                                                   [(a-testsuite "another suite" (a-testcase "a class" "another case" :fail))]])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :failedCount 2}]
           (accumulate-testsuite-failures-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" :fail))]
                                                   [(a-testsuite "suite" (a-testcase "a class" "a case" :fail))]])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :failedCount 1}]
           (accumulate-testsuite-failures-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" :pass))]
                                                   [(a-testsuite "suite" (a-testcase "a class" "a case" :fail))]])))
    (is (= [{:testsuite ["suite" "nested suite"]
             :classname "a class"
             :name "another case"
             :failedCount 1}
            {:testsuite ["suite" "nested suite"]
             :classname "a class"
             :name "a case"
             :failedCount 1}
            ]
           (accumulate-testsuite-failures-as-list [[(a-testsuite "suite"
                                                                 (a-testsuite "nested suite" (a-testcase "a class" "a case" :fail)))]
                                                   [(a-testsuite "suite"
                                                                 (a-testsuite "nested suite" (a-testcase "a class" "another case" :fail)))]]))))

  (testing "average-testcase-runtime"
    (is (= []
           (average-testcase-runtime [])))
    (is (= [{:name "suite"
             :children [{:name "a case"}]}]
           (average-testcase-runtime [[(a-testsuite "suite" (a-testcase "a case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :averageRuntime 42}]}]
           (average-testcase-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 42))]])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :children [{:name "the case" :averageRuntime 42}]}]}]
           (average-testcase-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "the case" 42))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :averageRuntime 20}]}]
           (average-testcase-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 30))]
                                      [(a-testsuite "suite" (a-testcase-with-runtime "a case" 10))]])))
    ;; should deal with fractions and round up
    (is (= [{:name "suite"
             :children [{:name "a case" :averageRuntime 21}]}]
           (average-testcase-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 30))]
                                      [(a-testsuite "suite" (a-testcase-with-runtime "a case" 11))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :averageRuntime 10}]}
            {:name "another suite"
             :children [{:name "another case" :averageRuntime 20}]}]
           (average-testcase-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 10))]
                                      [(a-testsuite "another suite" (a-testcase-with-runtime "another case" 20))]])))
    (is (= [{:name "suite"
             :children [{:name "nested suite"
                         :children [{:name "a case" :averageRuntime 10}
                                    {:name "another case" :averageRuntime 20}]}]}]
           (average-testcase-runtime [[(a-testsuite "suite"
                                                    (a-testsuite "nested suite" (a-testcase-with-runtime "a case" 10)))]
                                      [(a-testsuite "suite"
                                                    (a-testsuite "nested suite" (a-testcase-with-runtime "another case" 20)))]]))))

  (testing "average-testclass-runtime"
    (is (= []
           (average-testclass-runtime [])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :averageRuntime 40}]}]
           (average-testclass-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 40))]])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :averageRuntime 60}]}]
           (average-testclass-runtime [[(a-testsuite "suite"
                                                     (a-testcase-with-runtime "a class" "a case" 40)
                                                     (a-testcase-with-runtime "a class" "another case" 20))]])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :averageRuntime 30}]}]
           (average-testclass-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))]
                                       [(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 40))]])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :averageRuntime 30}]}]
           (average-testclass-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))]
                                       [(a-testsuite "suite" (a-testcase-with-runtime "a class" "another case" 40))]])))
    (is (= [{:name "suite"
             :children [{:name "nested suite"
                         :children [{:name "class"
                                     :averageRuntime 10}]}]}]
           (average-testclass-runtime [[(a-testsuite "suite"
                                                    (a-testsuite "nested suite"
                                                                 (a-testcase-with-runtime "class" "a case" 10)))]]))))

  (testing "average-testcase-runtime-as-list"
    (is (= []
           (average-testcase-runtime-as-list [])))
    (is (= [{:testsuite ["suite"] :classname "a class" :name "a case" :averageRuntime 42}]
           (average-testcase-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 42))]])))
    (is (= [{:testsuite ["suite"] :classname "a class" :name "a case" :averageRuntime 20}]
           (average-testcase-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 30))]
                                              [(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 10))]])))
    (is (= [{:testsuite ["another suite"] :classname "another class" :name "another case" :averageRuntime 20}
            {:testsuite ["suite"] :classname "a class" :name "a case" :averageRuntime 10}]
           (average-testcase-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 10))]
                                              [(a-testsuite "another suite" (a-testcase-with-runtime "another class" "another case" 20))]])))
    (is (= [{:testsuite ["suite" "nested suite"] :classname "another class" :name "another case" :averageRuntime 20}
            {:testsuite ["suite" "nested suite"] :classname "a class" :name "a case" :averageRuntime 10}]
           (average-testcase-runtime-as-list [[(a-testsuite "suite"
                                                            (a-testsuite "nested suite" (a-testcase-with-runtime "a class" "a case" 10)))]
                                              [(a-testsuite "suite"
                                                            (a-testsuite "nested suite" (a-testcase-with-runtime "another class" "another case" 20)))]])))))
