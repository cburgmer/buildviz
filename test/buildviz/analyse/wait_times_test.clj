(ns buildviz.analyse.wait-times-test
  (:require [buildviz.analyse.wait-times :as sut]
            [clojure.test :refer :all]))

(def a-day (* 24 60 60 1000))

(deftest test-wait-times-by-day
  (testing "should calculate wait time for triggered build"
    (is (= {"deploy" {"1970-01-01" 800}}
           (sut/wait-times-by-day [{:job "deploy"
                                    :build-id "42"
                                    :start 1000
                                    :end 2000
                                    :triggered-by {:job-name "test"
                                                   :build-id "41"}}
                                   {:job "test"
                                    :build-id "41"
                                    :end 200}]))))

  (testing "should handle optional 'end' value for triggering build"
    (is (empty? (sut/wait-times-by-day [{:job "deploy"
                                         :build-id "42"
                                         :start 1000
                                         :end 2000
                                         :triggered-by {:job-name "test"
                                                        :build-id "41"}}
                                        {:job "test"
                                         :build-id "41"}]))))

  (testing "should handle optional 'end' value for triggered build"
    (is (empty? (sut/wait-times-by-day [{:job "deploy"
                                         :build-id "42"
                                         :start 1000
                                         :triggered-by {:job-name "test"
                                                        :build-id "41"}}
                                        {:job "test"
                                         :build-id "41"
                                         :end 200}]))))

  (testing "should handle missing triggering build"
    (is (empty? (sut/wait-times-by-day [{:job "deploy"
                                         :build-id "42"
                                         :start 1000
                                         :end 2000
                                         :triggered-by {:job-name "test"
                                                        :build-id "41"}}]))))

  (testing "should average wait times"
    (is (= {"deploy" {"1970-01-01" 900}}
           (sut/wait-times-by-day [{:job "deploy"
                                    :build-id "42"
                                    :start 2000
                                    :end 3000
                                    :triggered-by {:job-name "test"
                                                   :build-id "41"}}
                                   {:job "test"
                                    :build-id "41"
                                    :end 1000}
                                   {:job "deploy"
                                    :build-id "30"
                                    :start 1000
                                    :end 2000
                                    :triggered-by {:job-name "test"
                                                   :build-id "30"}}
                                   {:job "test"
                                    :build-id "30"
                                    :end 200}]))))

  (testing "should list wait times by day"
    (is (= {"deploy" {"1970-01-01" 800
                      "1970-01-02" 1000}}
           (sut/wait-times-by-day [{:job "deploy"
                                    :build-id "42"
                                    :start (+ 2000 a-day)
                                    :end (+ 3000 a-day)
                                    :triggered-by {:job-name "test"
                                                   :build-id "41"}}
                                   {:job "test"
                                    :build-id "41"
                                    :end (+ 1000 a-day)}
                                   {:job "deploy"
                                    :build-id "30"
                                    :start 1000
                                    :end 2000
                                    :triggered-by {:job-name "test"
                                                   :build-id "30"}}
                                   {:job "test"
                                    :build-id "30"
                                    :end 200}])))))
