(ns buildviz.analyse.pipelines-test
  (:require [buildviz.analyse.pipelines :as sut]
            [clojure.test :refer :all]))

(def a-day (* 24 60 60 1000))

(defn- a-build [job build-id]
  {:job job
   :build-id build-id
   :start 0})

(defn- a-triggered-build
  ([job triggering-job triggering-build-id]
   (a-triggered-build job "21" triggering-job triggering-build-id))
  ([job build-id triggering-job triggering-build-id]
   {:job job
    :build-id build-id
    :start 0
    :end 10
    :triggered-by [{:job-name triggering-job
                    :build-id triggering-build-id}]}))

(deftest test-build-pipelines
  (testing "should find a chain of two builds"
    (is (= [["test" "deploy"]]
           (map :pipeline (sut/pipelines [(a-triggered-build "deploy" "test" "41")
                                          (a-build "test" "41")])))))

  (testing "should find chain of 3 builds"
    (is (= [["test" "deploy-staging" "deploy-live"]]
           (map :pipeline  (sut/pipelines [(a-triggered-build "deploy-live" "deploy-staging" "42")
                                           (a-triggered-build "deploy-staging" "42" "test" "41")
                                           (a-build "test" "41")])))))

  (testing "should handle shared chain"
    (is (= [["test" "deploy-qa"]
            ["test" "deploy-uat"]]
           (map :pipeline  (sut/pipelines [(a-build "test" "41")
                                           (a-triggered-build "deploy-qa" "test" "41")
                                           (a-triggered-build "deploy-uat" "test" "41")])))))

  (testing "should handle missing triggering build"
    (is (= []
           (sut/pipelines [(a-triggered-build "deploy-live" "deploy-staging" "42")
                           (a-triggered-build "deploy-staging" "42" "test" "41")]))))

  (testing "should ignore 'stand-alone' builds"
    (is (= []
           (sut/pipelines [(a-build "deploy" "42")
                           (a-build "test" "41")]))))

  (testing "should ignore 1-build pipeline due to missing triggering build"
    (is (= []
           (sut/pipelines [(a-triggered-build "deploy" "test" "41")]))))

  (testing "should ignore pipeline failing in last step"
    (is (= []
           (sut/pipelines [(-> (a-triggered-build "deploy" "test" "41")
                               (assoc :outcome "fail"))
                           (a-build "test" "41")]))))

  (testing "should include pipeline successful in last step"
    (is (= [["test" "deploy"]]
           (map :pipeline  (sut/pipelines [(-> (a-triggered-build "deploy" "test" "41")
                                               (assoc :outcome "pass"))
                                           (a-build "test" "41")])))))

  (testing "should handle missing end time"
    (is (= []
           (sut/pipelines [(-> (a-triggered-build "deploy" "test" "41")
                               (dissoc :end))
                           (a-build "test" "41")]))))

  (testing "should handle two runs of the same pipeline"
    (is (= [{:pipeline ["test" "deploy"]
             :builds [{:job "test"
                       :build-id "41"}
                      {:job "deploy"
                       :build-id "20"}]
             :start 86400200
             :end 86401000}
            {:pipeline ["test" "deploy"]
             :builds [{:job "test"
                       :build-id "40"}
                      {:job "deploy"
                       :build-id "18"}]
             :start 400
             :end 1000}]
           (sut/pipelines [{:job "deploy"
                            :build-id "20"
                            :triggered-by [{:job-name "test"
                                            :build-id "41"}]
                            :end (+ 1000 a-day)}
                           {:job "test"
                            :build-id "41"
                            :start (+ 200 a-day)}
                           {:job "deploy"
                            :build-id "18"
                            :triggered-by [{:job-name "test"
                                            :build-id "40"}]
                            :end 1000}
                           {:job "test"
                            :build-id "40"
                            :start 400}]))))

  (testing "should handle build triggered by two over two days"
    (is (= [{:pipeline ["test" "deploy"]
             :builds [{:job "test"
                       :build-id "41"}
                      {:job "deploy"
                       :build-id "42"}]
             :start 86400000
             :end 86400000}
            {:pipeline ["test" "deploy"]
             :builds [{:job "test"
                       :build-id "40"}
                      {:job "deploy"
                       :build-id "42"}]
             :start 0
             :end 86400000}]
           (sut/pipelines [{:job "deploy"
                            :build-id "42"
                            :triggered-by [{:job-name "test"
                                            :build-id "41"}
                                           {:job-name "test"
                                            :build-id "40"}]
                            :end a-day}
                           {:job "test"
                            :build-id "41"
                            :start a-day}
                           {:job "test"
                            :build-id "40"
                            :start 0}])))))
