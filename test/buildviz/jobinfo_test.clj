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
    (is (= 42 (average-runtime [{:start 0 :end 42}])))
    (is (= 20 (average-runtime [{:start 10 :end 20} {:start 50 :end 80}])))
    (is (= 42 (average-runtime [{:start 0 :end 42}, {:outcome "pass"}])))))
