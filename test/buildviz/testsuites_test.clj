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
                 :status :pass
                 :runtime value})
  ([classname name value] (merge (a-testcase-with-runtime name value)
                                 {:classname classname})))

(defn- a-testsuite [name & children]
  {:name name
   :children children})

(defn- dummy-test-lookup [tests]
  (fn [build-id]
    (get tests build-id)))


(deftest test-aggregate-testcase-info
  (testing "aggregate-testcase-info"
    (is (= []
           (aggregate-testcase-info [])))
    (is (= [{:name "suite"
             :children [{:name "a case" :failed-count 1}]}]
           (aggregate-testcase-info [[(a-testsuite "suite" (a-testcase "a case" :fail))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :average-runtime 42 :failed-count 0}]}]
           (aggregate-testcase-info [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 42))]])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :children [{:name "the case" :average-runtime 42 :failed-count 0}]}]}]
           (aggregate-testcase-info [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "the case" 42))]])))
    (is (= [{:name "suite"
             :children [{:name "a case" :average-runtime 20 :failed-count 0}]}]
           (aggregate-testcase-info [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 30))]
                                      [(a-testsuite "suite" (a-testcase-with-runtime "a case" 10))]])))
    ;; should deal with fractions and round up
    (is (= [{:name "suite"
             :children [{:name "a case" :average-runtime 21 :failed-count 0}]}]
           (aggregate-testcase-info [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 30))]
                                      [(a-testsuite "suite" (a-testcase-with-runtime "a case" 11))]])))
    (is (= [{:name "another suite"
             :children [{:name "another case" :average-runtime 20 :failed-count 0}]}
            {:name "suite"
             :children [{:name "a case" :average-runtime 10 :failed-count 0}]}]
           (aggregate-testcase-info [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 10))]
                                      [(a-testsuite "another suite" (a-testcase-with-runtime "another case" 20))]])))
    (is (= [{:name "suite"
             :children [{:name "nested suite"
                         :children [{:name "another case" :average-runtime 20 :failed-count 0}
                                    {:name "a case" :average-runtime 10 :failed-count 0}]}]}]
           (aggregate-testcase-info [[(a-testsuite "suite"
                                                    (a-testsuite "nested suite" (a-testcase-with-runtime "a case" 10)))]
                                      [(a-testsuite "suite"
                                                    (a-testsuite "nested suite" (a-testcase-with-runtime "another case" 20)))]])))

    (testing "should accumulate runtime of testcases with the same name/class/suite"
      (is (= [{:name "suite"
               :children [{:name "a class"
                           :children [{:name "a case"
                                       :average-runtime 35 :failed-count 0}]}]}]
             (aggregate-testcase-info [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 12))
                                         (a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 23))]]))))))

(deftest test-aggregate-testcase-info-as-list
  (testing "aggregate-testcase-info-as-list"
    (is (= []
           (aggregate-testcase-info-as-list [])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :average-runtime 42
             :failed-count 0}]
           (aggregate-testcase-info-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 42))]])))
    (is (= [{:testsuite ["suite"]
             :classname nil
             :name "a case"
             :average-runtime 42
             :failed-count 0}]
           (aggregate-testcase-info-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a case" 42))]])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :failed-count 1}]
           (aggregate-testcase-info-as-list [[(a-testsuite "suite" (a-testcase "a class" "a case" :failed))]])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :average-runtime 20
             :failed-count 0}]
           (aggregate-testcase-info-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 30))]
                                              [(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 10))]])))
    (is (= [{:testsuite ["suite"]
             :classname "a class"
             :name "a case"
             :average-runtime 10
             :failed-count 0}
            {:testsuite ["another suite"]
             :classname "another class"
             :name "another case"
             :average-runtime 20
             :failed-count 0}]
           (aggregate-testcase-info-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 10))]
                                              [(a-testsuite "another suite" (a-testcase-with-runtime "another class" "another case" 20))]])))
    (is (= [{:testsuite ["suite" "nested suite"]
             :classname "a class"
             :name "a case"
             :average-runtime 10
             :failed-count 0}
            {:testsuite ["suite" "nested suite"]
             :classname "another class"
             :name "another case"
             :average-runtime 20
             :failed-count 0}]
           (aggregate-testcase-info-as-list [[(a-testsuite "suite"
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
                         :average-runtime 40}]}]
           (average-testclass-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 40))]])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :average-runtime 60}]}]
           (average-testclass-runtime [[(a-testsuite "suite"
                                                     (a-testcase-with-runtime "a class" "a case" 40)
                                                     (a-testcase-with-runtime "a class" "another case" 20))]])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :average-runtime 30}]}]
           (average-testclass-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))]
                                       [(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 40))]])))
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :average-runtime 30}]}]
           (average-testclass-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))]
                                       [(a-testsuite "suite" (a-testcase-with-runtime "a class" "another case" 40))]])))
    (is (= [{:name "suite"
             :children [{:name "nested suite"
                         :children [{:name "class"
                                     :average-runtime 10}]}]}]
           (average-testclass-runtime [[(a-testsuite "suite"
                                                     (a-testsuite "nested suite"
                                                                  (a-testcase-with-runtime "class" "a case" 10)))]]))))

  (testing "should properly accumulate runtime with multiple same suite and class entries"
    (is (= [{:name "suite"
             :children [{:name "a class"
                         :average-runtime 60}]}]
           (average-testclass-runtime [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))
                                        (a-testsuite "suite" (a-testcase-with-runtime "a class" "another case" 40))]])))))

(deftest test-average-testclass-runtime-as-list
  (testing "average-testclass-runtime-as-list"
    (is (= []
           (average-testclass-runtime-as-list [])))
    (is (= [{:testsuite ["suite"] :classname "a class"}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite"
                                                             (a-testcase "a class" "a case" :fail)
                                                             (a-testcase-with-runtime "a class" "another case" 10))]]
                                              )))
    (is (= [{:testsuite ["suite"] :classname "a class" :average-runtime 40}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 40))]])))
    (is (= [{:testsuite ["suite"] :classname "a class" :average-runtime 60}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite"
                                                             (a-testcase-with-runtime "a class" "a case" 40)
                                                             (a-testcase-with-runtime "a class" "another case" 20))]])))
    (is (= [{:testsuite ["suite"] :classname "a class" :average-runtime 30}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))]
                                               [(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 40))]])))
    (is (= [{:testsuite ["suite"] :classname "a class" :average-runtime 30}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite" (a-testcase-with-runtime "a class" "a case" 20))]
                                               [(a-testsuite "suite" (a-testcase-with-runtime "a class" "another case" 40))]])))
    (is (= [{:testsuite ["suite" "nested suite"] :classname "class" :average-runtime 10}]
           (average-testclass-runtime-as-list [[(a-testsuite "suite"
                                                             (a-testsuite "nested suite"
                                                                          (a-testcase-with-runtime "class" "a case" 10)))]])))))

(def build-input-1 {:start 1 :inputs '({:revision "abcd" :source_id 21} {:revision "1" :source_id 42})})
(def another-build-input-1 {:start 2 :inputs '({:revision "abcd" :source_id 21} {:revision "1" :source_id 42})})
(def build-input-2 {:start 4 :inputs '({:revision "2" :source_id 42})})

(defn- the-testcase [status]
  (a-testsuite "a suite"
               (a-testcase "a class" "the testcase" status)))

(defn- another-testcase [status]
  (a-testsuite "some suite"
               (a-testcase "some class" "another testcase" status)))

(deftest test-flakt-testcases
  (testing "should return empty results"
    (is (= []
           (flaky-testcases {} (dummy-test-lookup {})))))

  (testing "should return map for flaky testcase"
    (is (= [{:name "a suite"
             :children [{:name "a class"
                         :children [{:name "the testcase"
                                     :latest-build-id "failed-run-id"
                                     :latest-failure (:start another-build-input-1)
                                     :flaky-count 1}]}]}]
           (flaky-testcases {"passed-run-id" build-input-1
                             "failed-run-id" another-build-input-1}
                            (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)]
                                                "failed-run-id" [(the-testcase :fail)]})))))

  (testing "should include two flaky tests of same class"
    (is (= [{:name "a suite"
             :children [{:name "nested suite"
                         :children [{:name "a class"
                                     :children [{:name "second test"
                                                 :latest-build-id "failed-run-id"
                                                 :latest-failure (:start another-build-input-1)
                                                 :flaky-count 1}
                                                {:name "first test"
                                                 :latest-build-id "failed-run-id"
                                                 :latest-failure (:start another-build-input-1)
                                                 :flaky-count 1}]}]}]}]
           (flaky-testcases {"passed-run-id" build-input-1
                             "failed-run-id" another-build-input-1}
                            (dummy-test-lookup {"passed-run-id" [(a-testsuite "a suite"
                                                                              (a-testsuite "nested suite"
                                                                                          (a-testcase "a class" "first test" :pass)
                                                                                          (a-testcase "a class" "second test" :pass)))]
                                                "failed-run-id" [(a-testsuite "a suite"
                                                                              (a-testsuite "nested suite"
                                                                                          (a-testcase "a class" "first test" :fail)
                                                                                          (a-testcase "a class" "second test" :fail)))]}))))))

(deftest test-flaky-testcases-as-list
  (testing "flaky-testcases-as-list"
    (is (= []
           (flaky-testcases-as-list {}
                                    (dummy-test-lookup {}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :latest-build-id "failed-run-id"
             :latest-failure (:start another-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" build-input-1
                                     "failed-run-id" another-build-input-1}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :fail)]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :latest-build-id "failed-run-id"
             :latest-failure (:start another-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" build-input-1
                                     "failed-run-id" another-build-input-1}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :error)]}))))
    (is (= []
           (flaky-testcases-as-list {"passed-run-id" build-input-1
                                     "failed-run-id" another-build-input-1}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :pass)]}))))
    (is (= [{:testsuite ["some suite"]
             :classname "some class"
             :name "another testcase"
             :latest-build-id "failed-run-id"
             :latest-failure (:start another-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" build-input-1
                                     "failed-run-id" another-build-input-1}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)
                                                                         (another-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :pass)
                                                                         (another-testcase :fail)]}))))
    (is (= []
           (flaky-testcases-as-list {"failed-run-id" build-input-1}
                                    (dummy-test-lookup {"failed-run-id" [(the-testcase :fail)]}))))
    (is (= []
           (flaky-testcases-as-list {"passed-run-id" build-input-2
                                     "failed-run-id" build-input-1}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :fail)]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :latest-build-id "failed-run-id"
             :latest-failure (:start another-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" build-input-1
                                     "failed-run-id" another-build-input-1
                                     "another-failed-run-id" build-input-2}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)
                                                                         (another-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :fail)]
                                                        "another-failed-run-id" [(another-testcase :fail)]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :latest-build-id "failed-run-id"
             :latest-failure (:start build-input-1)
             :flaky-count 1}
            {:testsuite ["some suite"]
             :classname "some class"
             :name "another testcase"
             :latest-build-id "another-failed-run-id"
             :latest-failure (:start another-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"passed-run-id" build-input-1
                                     "failed-run-id" build-input-1
                                     "another-failed-run-id" another-build-input-1}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)
                                                                         (another-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :fail)]
                                                        "another-failed-run-id" [(another-testcase :fail)]}))))
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "the testcase"
             :latest-build-id "another-failed-run-id"
             :latest-failure (:start another-build-input-1)
             :flaky-count 2}]
           (flaky-testcases-as-list {"passed-run-id" build-input-1
                                     "failed-run-id" build-input-1
                                     "another-failed-run-id" another-build-input-1}
                                    (dummy-test-lookup {"passed-run-id" [{:name "a suite"
                                                                          :children [{:name "the testcase"
                                                                                      :classname "a class"
                                                                                      :status :pass}]}]
                                                        "failed-run-id" [{:name "a suite"
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
             :latest-build-id "another-failed-run-id"
             :latest-failure (:start build-input-2)
             :flaky-count 2}]
           (flaky-testcases-as-list {"passed-run-id" build-input-1
                                     "failed-run-id" another-build-input-1
                                     "another-passed-run-id" build-input-2
                                     "another-failed-run-id" build-input-2}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :fail)]
                                                        "another-passed-run-id" [(the-testcase :pass)]
                                                        "another-failed-run-id" [(the-testcase :fail)]}))))
    (is (= []
           (flaky-testcases-as-list {"passed-run-id" {}
                                     "failed-run-id" {}}
                                    (dummy-test-lookup {"passed-run-id" [(the-testcase :pass)]
                                                        "failed-run-id" [(the-testcase :fail)]})))))

  (testing "should not find flaky tests in two failing builds where tests fails consistently"
    (is (= []
           (flaky-testcases-as-list {"first-failed-run-id" build-input-1
                                     "second-failed-run-id" another-build-input-1}
                                    (dummy-test-lookup {"first-failed-run-id" [(the-testcase :fail)]
                                                        "second-failed-run-id" [(the-testcase :fail)]})))))

  (testing "should find flaky test in two failing builds"
    (is (= [{:testsuite ["a suite"]
             :classname "a class"
             :name "flaky testcase"
             :latest-build-id "second-failed-run-id"
             :latest-failure (:start another-build-input-1)
             :flaky-count 1}]
           (flaky-testcases-as-list {"first-failed-run-id" build-input-1
                                     "second-failed-run-id" another-build-input-1}
                                    (dummy-test-lookup {"first-failed-run-id" [(a-testsuite "a suite"
                                                                                            (a-testcase "a class" "flaky testcase" :pass)
                                                                                            (a-testcase "a class" "const failing testcase" :fail))]
                                                        "second-failed-run-id" [(a-testsuite "a suite"
                                                                                             (a-testcase "a class" "flaky testcase" :fail)
                                                                                             (a-testcase "a class" "const failing testcase" :fail))]}))))))
