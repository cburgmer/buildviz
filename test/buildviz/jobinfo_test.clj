(ns buildviz.jobinfo-test
  (:use clojure.test
        buildviz.jobinfo)
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]))


(def failed-test-input-1 {:outcome "fail" :inputs '({:revision "1" :source-id 42})})
(def passed-test-input-1 {:outcome "pass" :inputs '({:revision "1" :source-id 42})})
(def passed-test-input-2 {:outcome "pass" :inputs '({:revision "2" :source-id 42})})

(def failed-test-multiple-inputs-changed {:outcome "fail" :inputs '({:revision "1" :source-id 42}
                                                                    {:revision "a" :source-id 43})})
(def passed-test-multiple-inputs-changed {:outcome "pass" :inputs '({:revision "1" :source-id 42}
                                                                    {:revision "b" :source-id 43})})

(def sometime-on-1986-10-14 (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))
(def sometime-on-1988-1-1 (tc/to-long (t/from-time-zone (t/date-time 1988 1 1 14 3 27 456) (t/default-time-zone))))

(deftest test-builds-with-outcome
  (testing "builds-with-outcome"
    (is (= [] (builds-with-outcome [])))
    (is (= [] (builds-with-outcome [{:start 0}])))
    (is (= [{:outcome "pass"}] (builds-with-outcome [{:outcome "pass"}])))
    (is (= [{:outcome "pass"} {:outcome "fail"}] (builds-with-outcome [{:outcome "pass"} {:outcome "fail"}])))))

(deftest test-flaky-build-count
  (testing "flaky-build-count"
     (is (= 1 (flaky-build-count [failed-test-input-1 passed-test-input-1])))
     (is (= 1 (flaky-build-count [passed-test-input-1 failed-test-input-1])))
     (is (= 1 (flaky-build-count [{:outcome "pass" :inputs '({:revision "b" :source-id 43}
                                                             {:revision "1" :source-id 42})}
                                  {:outcome "fail" :inputs '({:revision "1" :source-id 42}
                                                             {:revision "b" :source-id 43})}])))
     (is (= 0 (flaky-build-count [{:outcome "pass" :inputs '({:revision "1" :source-id 42}
                                                             {:revision "b" :source-id 43})}
                                  {:outcome "fail" :inputs '({:revision "2" :source-id 42}
                                                             {:revision "b" :source-id 43})}])))
     (is (= 0 (flaky-build-count [passed-test-input-1 passed-test-input-1])))
     (is (= 0 (flaky-build-count [failed-test-input-1 passed-test-input-2])))
     (is (= 0 (flaky-build-count [failed-test-multiple-inputs-changed passed-test-multiple-inputs-changed])))
     (is (= 1 (flaky-build-count [passed-test-input-1 failed-test-input-1 failed-test-input-1 passed-test-input-1])))
     (is (= 0 (flaky-build-count [passed-test-input-1])))
     (is (= 0 (flaky-build-count [])))
     (is (= 0 (flaky-build-count [{:outcome "pass"} {:outcome "fail"}])))))

(deftest test-average-runtime
  (testing "average-runtime"
     (is (= nil (average-runtime [])))
     (is (= nil (average-runtime [{:start 0}])))
     (is (= 42 (average-runtime [{:start 0 :end 42}])))
     (is (= 20 (average-runtime [{:start 10 :end 20} {:start 50 :end 80}])))
     (is (= 2 (average-runtime [{:start 1 :end 2} {:start 3 :end 5}])))
     (is (= 42 (average-runtime [{:start 0 :end 42}, {:outcome "pass"}])))))

(deftest test-average-runtime-by-day
  (testing "average-runtime-by-day"
     (is (= {} (average-runtime-by-day [])))
     (is (= {} (average-runtime-by-day [{:start 0}])))
     (is (= {"1986-10-14" 1000}
            (average-runtime-by-day [{:start sometime-on-1986-10-14 :end (+ sometime-on-1986-10-14 1000)}])))
     (is (= {"1986-10-15" (* 24 60 60 1000)}
            (average-runtime-by-day [{:start sometime-on-1986-10-14 :end (+ sometime-on-1986-10-14 (* 24 60 60 1000))}])))
     (is (= {"1986-10-14" 2001}
            (average-runtime-by-day [{:start sometime-on-1986-10-14 :end (+ sometime-on-1986-10-14 1000)}
                                     {:start sometime-on-1986-10-14 :end (+ sometime-on-1986-10-14 3001)}])))
     (is (= {"1986-10-14" 1000
             "1988-01-01" 3001}
            (average-runtime-by-day [{:start sometime-on-1986-10-14 :end (+ sometime-on-1986-10-14 1000)}
                                     {:start sometime-on-1988-1-1 :end (+ sometime-on-1988-1-1 3001)}]))))
  (testing "should handle integers"
    (is (= {"1970-01-01" 0}
           (average-runtime-by-day [{:start (int 0) :end (int 0)}])))))

(deftest test-fail-count
  (testing "fail-count"
     (is (= 0 (fail-count [{:outcome "pass"}])))
     (is (= 0 (fail-count [])))
     (is (= 1 (fail-count [{:outcome "fail"}])))
     (is (= 2 (fail-count [{:outcome "fail"} {:outcome "pass"} {:outcome "fail"}])))))
