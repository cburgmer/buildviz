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

(def failed-build-input-1 {:outcome "fail" :start 1 :inputs '({:revision "abcd" :id 21} {:revision "1" :id 42})})
(def another-failed-build-input-1 {:outcome "fail" :start 2 :inputs '({:revision "abcd" :id 21} {:revision "1" :id 42})})
(def passed-build-input-1 {:outcome "pass" :start 3 :inputs '({:revision "1" :id 42} {:revision "abcd" :id 21})})
(def failed-build-input-2 {:outcome "fail" :start 4 :inputs '({:revision "2" :id 42})})
(def passed-build-input-2 {:outcome "pass" :start 5 :inputs '({:revision "2" :id 42})})

(defn- dummy-test-lookup [tests]
  (fn [build-id]
    (get tests build-id)))

(deftest test-accumulate-testsuite-failures
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

  (testing "should count multiple failures as one for testcases with the same name/class/suite"
    (is (= [{:name "suite"
             :children [{:name "a case" :failedCount 1}]}]
           (accumulate-testsuite-failures [[(a-testsuite "suite" (a-testcase "a case" :fail))
                                            (a-testsuite "suite" (a-testcase "a case" :fail))
                                            (a-testsuite "suite" (a-testcase "a case" :pass))]])))))

(deftest test-accumulate-testsuite-failures-as-list
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
                                                                 (a-testsuite "nested suite" (a-testcase "a class" "another case" :fail)))]])))))

(deftest test-average-testcase-runtime
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
                                                    (a-testsuite "nested suite" (a-testcase-with-runtime "another case" 20)))]])))

    (testing "should accumulate runtime of testcases with the same name/class/suite"
      (is (= [{:name "suite"
               :children [{:name "a class"
                           :children [{:name "a case"
                                       :averageRuntime 35}]}]}]
             (average-testcase-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 12))
                                         (a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 23))]]))))))

(deftest test-average-testcase-runtime-as-list
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

(deftest test-average-testclass-runtime
  (testing "average-testclass-runtime"
    (is (= []
           (average-testclass-runtime [])))
    (is (= [{:name "suite"
             :children [{:name "a class"}]}]
           (average-testclass-runtime [[(a-testsuite "suite"
                                                     (a-testcase "a class" "a case" :fail)
                                                     (a-testcase-with-runtime "a class" "another case" 10))]])))
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

  (testing "should properly accumulate runtime with multiple same suite and class entries"
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :averageRuntime 60}]}]
           (average-testclass-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))
                                        (a-testsuite "suite" (a-testcase-with-runtime "a class" "another case" 40))]])))))

(deftest test-average-testclass-runtime-as-list
  (testing "average-testclass-runtime-as-list"
    (is (= []
           (average-testclass-runtime-as-list [])))
    (is (= [{:testsuite ["suite"] :classname "a class" :averageRuntime nil}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite"
                                                             (a-testcase "a class" "a case" :fail)
                                                             (a-testcase-with-runtime "a class" "another case" 10))]]
                                              )))
    (is (= [{:testsuite ["suite"] :classname "a class" :averageRuntime 40}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 40))]])))
    (is (= [{:testsuite ["suite"] :classname "a class" :averageRuntime 60}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite"
                                                             (a-testcase-with-runtime "a class" "a case" 40)
                                                             (a-testcase-with-runtime "a class" "another case" 20))]])))
    (is (= [{:testsuite ["suite"] :classname "a class" :averageRuntime 30}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))]
                                               [(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 40))]])))
    (is (= [{:testsuite ["suite"] :classname "a class" :averageRuntime 30}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))]
                                               [(a-testsuite "suite" (a-testcase-with-runtime "a class" "another case" 40))]])))
    (is (= [{:testsuite ["suite" "nested suite"] :classname "class" :averageRuntime 10}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite"
                                                             (a-testsuite "nested suite"
                                                                          (a-testcase-with-runtime "class" "a case" 10)))]])))))

(deftest test-flaky-testcases-as-list
  (testing "flaky-testcases-as-list"
    (is (= []
           (flaky-testcases-as-list {}
                                    (dummy-test-lookup {}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :build-id "failed-run-id"
             :latest-failure (:start failed-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-1
                                     "failed-run-id" failed-build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :fail))]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :build-id "failed-run-id"
             :latest-failure (:start failed-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-1
                                     "failed-run-id" failed-build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :error))]}))))
    (is (= []
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-1
                                     "failed-run-id" failed-build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :pass))]}))))
    (is (= [{:testsuite ["some suite"]
             :classname "some class"
             :name "another testcase"
             :build-id "failed-run-id"
             :latest-failure (:start failed-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-1
                                     "failed-run-id" failed-build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :pass))
                                                                         (a-testsuite "some suite"
                                                                                      (a-testcase "some class" "another testcase" :fail))]}))))
    (is (= []
           (flaky-testcases-as-list {"failed-run-id" failed-build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :fail))]}))))
    (is (= []
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-2
                                     "failed-run-id" failed-build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :fail))]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :build-id "failed-run-id"
             :latest-failure (:start failed-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-1
                                     "failed-run-id" failed-build-input-1
                                     "another-failed-run-id" failed-build-input-2}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :fail))]
                                                        "another-failed-run-id" [(a-testsuite "another suite"
                                                                                              (a-testcase "another class" "another testcase" :fail))]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :build-id "failed-run-id"
             :latest-failure (:start failed-build-input-1)
             :flaky-count 1}
            {:testsuite ["another suite"]
             :classname "another class"
             :name "another testcase"
             :build-id "another-failed-run-id"
             :latest-failure (:start another-failed-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-1
                                     "failed-run-id" failed-build-input-1
                                     "another-failed-run-id" another-failed-build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :fail))]
                                                        "another-failed-run-id" [(a-testsuite "another suite"
                                                                                              (a-testcase "another class" "another testcase" :fail))]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :build-id "another-failed-run-id"
             :latest-failure (:start another-failed-build-input-1)
             :flaky-count 2}]
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-1
                                     "failed-run-id" failed-build-input-1
                                     "another-failed-run-id" another-failed-build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [{:name "a suite"
                                                                          :children [{:name "the testcase"
                                                                                      :classname "a class"
                                                                                      :status :fail
                                                                                      :runtime 42}]}]
                                                        "another-failed-run-id" [{:name "a suite"
                                                                                  :children [{:name "the testcase"
                                                                                              :classname "a class"
                                                                                              :status :fail
                                                                                              :runtime 1}]}]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :build-id "another-failed-run-id"
             :latest-failure (:start failed-build-input-2)
             :flaky-count 2}]
           (flaky-testcases-as-list {"passed-run-id" passed-build-input-1
                                     "failed-run-id" failed-build-input-1
                                     "another-passed-run-id" passed-build-input-2
                                     "another-failed-run-id" failed-build-input-2}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :fail))]
                                                        "another-failed-run-id" [(a-testsuite "a suite"
                                                                                              (a-testcase "a class" "the testcase" :fail))]}))))
    (is (= []
           (flaky-testcases-as-list {"passed-run-id" {:outcome "pass"}
                                     "failed-run-id" {:outcome "fail"}}
                                    (dummy-test-lookup {"failed-run-id" [(a-testsuite "a suite"
                                                                                      (a-testcase "a class" "the testcase" :fail))]}))))))
