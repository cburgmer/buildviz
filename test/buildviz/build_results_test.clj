(ns buildviz.build-results-test
  (:use clojure.test)
  (:require [buildviz.build-results :as results]))


(deftest test-build-data-validation-errors
  (testing "should pass on 0 end time"
    (is (empty? (results/build-data-validation-errors {:end 0}))))

  (testing "should fail on negative end time"
    (is (not (empty? (results/build-data-validation-errors {:end -1})))))

  (testing "should fail on end time before start time"
    (is (not (empty? (results/build-data-validation-errors {:start 42
                                                            :end 41}))))))

(deftest test-build-results
  (testing "should return tests for a build"
    (let [load-tests (fn [job-name build-id]
                       (get-in {"aJob" {"1" "<thexml>"}}
                               [job-name build-id]))
          build-results (results/build-results {} load-tests)]
      (is (= "<thexml>"
             (results/tests build-results "aJob" "1")))))

  (testing "should return all tests for existing builds"
    (let [load-tests (fn [job-name build-id]
                       (get-in {"aJob" {"1" "<thexml>"
                                        "2" "<morexml>"}
                                "anotherJob" {"3" "<somemorexml>"}}
                               [job-name build-id]))
          build-results (results/build-results {"aJob" {"1" {}
                                                        "2" {}
                                                        "4" {}}
                                                "anotherJob" {"3" {}}} load-tests)]
      (is (= '("<thexml>" "<morexml>")
             (results/chronological-tests build-results "aJob" nil)))))

  (testing "should return all tests after a given timestamp"
    (let [load-tests (fn [job-name build-id]
                       (get-in {"aJob" {"1" "<thexml>"
                                        "2" "<morexml>"
                                        "4" "<evenmorexml>"}
                                "anotherJob" {"3" "<somemorexml>"}}
                               [job-name build-id]))
          build-results (results/build-results {"aJob" {"1" {:start 42}
                                                        "2" {:start 100}
                                                        "4" {:start 200}}
                                                "anotherJob" {"3" {:start 150}}} load-tests)]
      (is (= '("<evenmorexml>" "<morexml>")
             (results/chronological-tests build-results "aJob" 100))))))
