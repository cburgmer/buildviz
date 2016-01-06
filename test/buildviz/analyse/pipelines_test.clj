(ns buildviz.analyse.pipelines-test
  (:require [buildviz.analyse.pipelines :as sut]
            [clojure.test :refer :all]))

(deftest test-build-pipelines
  (testing "should find a chain of two builds"
    (is (= [["test" "deploy"]]
           (sut/find-pipelines [{:job "deploy"
                                 :build-id "42"
                                 :triggered-by {:job-name "test"
                                                :build-id "41"}}
                                {:job "test"
                                 :build-id "41"}]))))

  (testing "should find chain of 3 builds"
    (is (= [["test" "deploy-staging" "deploy-live"]]
           (sut/find-pipelines [{:job "deploy-live"
                                 :build-id "42"
                                 :triggered-by {:job-name "deploy-staging"
                                                :build-id "42"}}
                                {:job "deploy-staging"
                                 :build-id "42"
                                 :triggered-by {:job-name "test"
                                                :build-id "41"}}
                                {:job "test"
                                 :build-id "41"}]))))

  (testing "should handle shared chain"
    (is (= [["test" "deploy-qa"]
            ["test" "deploy-uat"]]
           (sut/find-pipelines [{:job "test"
                                 :build-id 41}
                                {:job "deploy-qa"
                                 :triggered-by {:job-name "test"
                                                :build-id 41}}
                                {:job "deploy-uat"
                                 :triggered-by {:job-name "test"
                                                :build-id 41}}]))))

  (testing "should handle missing triggering build"
    (is (= [["deploy-staging" "deploy-live"]]
           (sut/find-pipelines [{:job "deploy-live"
                                 :build-id "42"
                                 :triggered-by {:job-name "deploy-staging"
                                                :build-id "42"}}
                                {:job "deploy-staging"
                                 :build-id "42"
                                 :triggered-by {:job-name "test"
                                                :build-id "41"}}]))))

  (testing "should ignore 'stand-alone' builds"
    (is (= []
           (sut/find-pipelines [{:job "deploy"
                                 :build-id "42"}
                                {:job "test"
                                 :build-id "41"}]))))

  (testing "should ignore 1-build pipeline due to missing triggering build"
    (is (= []
           (sut/find-pipelines [{:job "deploy"
                                 :build-id "42"
                                 :triggered-by {:job-name "test"
                                                :build-id "40"}}])))))
