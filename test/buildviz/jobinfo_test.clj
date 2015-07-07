(ns buildviz.jobinfo-test
  (:use clojure.test
        buildviz.jobinfo))


(def failed-test-input-1 {:outcome "fail" :inputs '({:revision "1" :id 42})})
(def passed-test-input-1 {:outcome "pass" :inputs '({:revision "1" :id 42})})
(def passed-test-input-2 {:outcome "pass" :inputs '({:revision "2" :id 42})})

(def failed-test-multiple-inputs-changed {:outcome "fail" :inputs '({:revision "1" :id 42}
                                                                    {:revision "a" :id 43})})
(def passed-test-multiple-inputs-changed {:outcome "pass" :inputs '({:revision "1" :id 42}
                                                                    {:revision "b" :id 43})})

(deftest JobInfo
  (testing "builds-with-outcome"
    (is (= [] (builds-with-outcome [])))
    (is (= [] (builds-with-outcome [{:start 0}])))
    (is (= [{:outcome "pass"}] (builds-with-outcome [{:outcome "pass"}])))
    (is (= [{:outcome "pass"} {:outcome "fail"}] (builds-with-outcome [{:outcome "pass"} {:outcome "fail"}]))))

  (testing "flaky-build-count"
    (is (= 1 (flaky-build-count [failed-test-input-1 passed-test-input-1])))
    (is (= 1 (flaky-build-count [passed-test-input-1 failed-test-input-1])))
    (is (= 0 (flaky-build-count [passed-test-input-1 passed-test-input-1])))
    (is (= 0 (flaky-build-count [failed-test-input-1 passed-test-input-2])))
    (is (= 0 (flaky-build-count [failed-test-multiple-inputs-changed passed-test-multiple-inputs-changed])))
    (is (= 1 (flaky-build-count [passed-test-input-1 failed-test-input-1 failed-test-input-1 passed-test-input-1])))
    (is (= 0 (flaky-build-count [passed-test-input-1])))
    (is (= 0 (flaky-build-count [])))
    (is (= 0 (flaky-build-count [{:outcome "pass"} {:outcome "fail"}]))))

  (testing "average-runtime"
    (is (= nil (average-runtime [])))
    (is (= nil (average-runtime [{:start 0}])))
    (is (= nil (average-runtime [{:end 0}])))
    (is (= 42 (average-runtime [{:start 0 :end 42}])))
    (is (= 20 (average-runtime [{:start 10 :end 20} {:start 50 :end 80}])))
    (is (= 2 (average-runtime [{:start 1 :end 2} {:start 3 :end 5}])))
    (is (= 42 (average-runtime [{:start 0 :end 42}, {:outcome "pass"}]))))

  (testing "fail-count"
    (is (= 0 (fail-count [{:outcome "pass"}])))
    (is (= 0 (fail-count [])))
    (is (= 1 (fail-count [{:outcome "fail"}])))
    (is (= 2 (fail-count [{:outcome "fail"} {:outcome "pass"} {:outcome "fail"}])))))
