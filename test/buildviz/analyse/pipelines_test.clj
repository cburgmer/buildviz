(ns buildviz.analyse.pipelines-test
  (:require [buildviz.analyse.pipelines :as sut]
            [clojure.test :refer :all]))

(def a-day (* 24 60 60 1000))

(deftest test-build-pipelines
  (testing "should find a chain of two builds"
    (is (= [["test" "deploy"]]
           (keys (sut/pipeline-runtimes-by-day [{:job "deploy"
                                                 :end 10
                                                 :triggered-by {:job-name "test"
                                                                :build-id "41"}}
                                                {:job "test"
                                                 :build-id "41"
                                                 :start 0}])))))

  (testing "should find chain of 3 builds"
    (is (= [["test" "deploy-staging" "deploy-live"]]
           (keys (sut/pipeline-runtimes-by-day [{:job "deploy-live"
                                                 :end 10
                                                 :triggered-by {:job-name "deploy-staging"
                                                                :build-id "42"}}
                                                {:job "deploy-staging"
                                                 :build-id "42"
                                                 :triggered-by {:job-name "test"
                                                                :build-id "41"}}
                                                {:job "test"
                                                 :build-id "41"
                                                 :start 0}])))))

  (testing "should handle shared chain"
    (is (= [["test" "deploy-qa"]
            ["test" "deploy-uat"]]
           (keys (sut/pipeline-runtimes-by-day [{:job "test"
                                                 :build-id 41
                                                 :start 0}
                                                {:job "deploy-qa"
                                                 :end 10
                                                 :triggered-by {:job-name "test"
                                                                :build-id 41}}
                                                {:job "deploy-uat"
                                                 :end 10
                                                 :triggered-by {:job-name "test"
                                                                :build-id 41}}])))))

  (testing "should handle missing triggering build"
    (is (= [["deploy-staging" "deploy-live"]]
           (keys (sut/pipeline-runtimes-by-day [{:job "deploy-live"
                                                 :end 10
                                                 :triggered-by {:job-name "deploy-staging"
                                                                :build-id "42"}}
                                                {:job "deploy-staging"
                                                 :build-id "42"
                                                 :start 0
                                                 :triggered-by {:job-name "test"
                                                                :build-id "41"}}])))))

  (testing "should ignore 'stand-alone' builds"
    (is (= {}
           (sut/pipeline-runtimes-by-day [{:job "deploy"
                                           :build-id "42"}
                                          {:job "test"
                                           :build-id "41"}]))))

  (testing "should ignore 1-build pipeline due to missing triggering build"
    (is (= {}
           (sut/pipeline-runtimes-by-day [{:job "deploy"
                                           :build-id "42"
                                           :triggered-by {:job-name "test"
                                                          :build-id "40"}}]))))

  (testing "should ignore pipeline failing in last step"
    (is (= {}
           (sut/pipeline-runtimes-by-day [{:job "deploy"
                                           :build-id "42"
                                           :triggered-by {:job-name "test"
                                                          :build-id "41"}
                                           :outcome "fail"}
                                          {:job "test"
                                           :build-id "41"}]))))

  (testing "should include pipeline successful in last step"
    (is (= [["test" "deploy"]]
           (keys (sut/pipeline-runtimes-by-day [{:job "deploy"
                                                 :end 10
                                                 :triggered-by {:job-name "test"
                                                                :build-id "41"}
                                                 :outcome "pass"}
                                                {:job "test"
                                                 :build-id "41"
                                                 :start 0}])))))

  (testing "should aggregate multiple instances of a pipeline run"
    (is (= {["test" "deploy"] {"1970-01-01" 600
                               "1970-01-02" 800}}
           (sut/pipeline-runtimes-by-day [{:job "deploy"
                                           :triggered-by {:job-name "test"
                                                          :build-id "41"}
                                           :end (+ 1000 a-day)}
                                          {:job "test"
                                           :build-id "41"
                                           :start (+ 200 a-day)}
                                          {:job "deploy"
                                           :triggered-by {:job-name "test"
                                                          :build-id "40"}
                                           :end 1000}
                                          {:job "test"
                                           :build-id "40"
                                           :start 400}]))))

  (testing "should average multiple runs on one day"
    (is (= {["test" "deploy"] {"1970-01-01" 700}}
           (sut/pipeline-runtimes-by-day [{:job "deploy"
                                           :build-id "42"
                                           :triggered-by {:job-name "test"
                                                          :build-id "41"}
                                           :end 1000}
                                          {:job "test"
                                           :build-id "41"
                                           :start 200}
                                          {:job "deploy"
                                           :build-id "41"
                                           :triggered-by {:job-name "test"
                                                          :build-id "40"}
                                           :end 1000}
                                          {:job "test"
                                           :build-id "40"
                                           :start 400}])))))
