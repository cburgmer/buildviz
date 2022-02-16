(ns buildviz.analyse.wait-times-test
  (:require [buildviz.analyse.wait-times :as sut]
            [clojure.test :refer :all]))

(def a-day (* 24 60 60 1000))

(defn- a-build [job build-id end]
  {:job job
   :build-id build-id
   :end end
   :start 0})

(defn- a-triggered-build [job build-id start triggered-by]
  {:job job
   :build-id build-id
   :start start
   :end (+ start 1000)
   :triggered-by (map (fn [[triggering-job triggering-build-id]]
                        {:job-name triggering-job
                         :build-id triggering-build-id})
                      triggered-by)})

(deftest test-wait-times
  (testing "should calculate wait time for triggered build"
    (is (= [{:job "deploy" :build-id "42" :start 1000 :wait-time 800 :triggered-by {:job "test" :build-id "41"}}]
           (sut/wait-times [(a-triggered-build "deploy" "42" 1000 [["test" "41"]])
                            (a-build "test" "41" 200)]))))

  (testing "should use longest wait time for build triggered by two builds of the same job"
    (is (= [{:job "deploy" :build-id "42" :start 1000 :wait-time 900 :triggered-by {:job "test" :build-id "40"}}]
           (sut/wait-times [(a-triggered-build "deploy" "42" 1000 [["test" "41"]
                                                                   ["test" "40"]])
                            (a-build "test" "41" 500)
                            (a-build "test" "40" 100)]))))

  (testing "should use shortest wait time for build triggered by two builds of different jobs"
    (is (= [{:job "deploy" :build-id "42" :start 1000 :wait-time 500 :triggered-by {:job "another test" :build-id "41"}}]
           (sut/wait-times [(a-triggered-build "deploy" "42" 1000 [["another test" "41"]
                                                                   ["test" "40"]])
                            (a-build "another test" "41" 500)
                            (a-build "test" "40" 100)]))))

  (testing "should handle optional 'end' value for triggering build"
    (is (empty? (sut/wait-times [(a-triggered-build "deploy" "42" 1000 [["test" "41"]])
                                 (-> (a-build "test" "41" 200)
                                     (dissoc :end))]))))

  (testing "should not be affected by missing 'end' value for triggered build"
    (is (= [{:job "deploy" :build-id "42" :start 1000 :wait-time 800 :triggered-by {:job "test" :build-id "41"}}]
           (sut/wait-times [(-> (a-triggered-build "deploy" "42" 1000 [["test" "41"]])
                                (dissoc :end))
                            (a-build "test" "41" 200)]))))

  (testing "should handle missing triggering build"
    (is (empty? (sut/wait-times [(a-triggered-build "deploy" "42" 1000 [["test" "41"]])]))))

  (testing "should handle partially missing triggering builds"
    (is (= [{:job "deploy" :build-id "42" :start 1000 :wait-time 900 :triggered-by {:job "test" :build-id "40"}}]
           (sut/wait-times [(a-triggered-build "deploy" "42" 1000 [["another test" "41"]
                                                                   ["test" "40"]
                                                                   ["yet another test" "39"]])
                            (a-build "test" "40" 100)]))))

  (testing "should handle two builds"
    (is (= [{:job "deploy" :build-id "42" :start 2000 :wait-time 1000 :triggered-by {:job "test" :build-id "41"}}
            {:job "deploy" :build-id "30" :start 1000 :wait-time 800 :triggered-by {:job "test" :build-id "30"}}]
           (sut/wait-times [(a-triggered-build "deploy" "42" 2000 [["test" "41"]])
                            (a-build "test" "41" 1000)
                            (a-triggered-build "deploy" "30" 1000 [["test" "30"]])
                            (a-build "test" "30" 200)])))))
