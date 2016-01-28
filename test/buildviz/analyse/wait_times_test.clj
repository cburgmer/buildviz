(ns buildviz.analyse.wait-times-test
  (:require [buildviz.analyse.wait-times :as sut]
            [clojure.test :refer :all]))

(def a-day (* 24 60 60 1000))

(defn- a-build [job build-id end]
  {:job job
   :build-id build-id
   :end end
   :start 0})

(defn- a-triggered-build
  ([job start triggering-job triggering-build-id]
   (a-triggered-build job "42" start triggering-job triggering-build-id))
  ([job build-id start triggering-job triggering-build-id]
   {:job job
    :build-id build-id
    :start start
    :end (+ start 1000)
    :triggered-by [{:job-name triggering-job
                    :build-id triggering-build-id}]}))

(deftest test-wait-times-by-day
  (testing "should calculate wait time for triggered build"
    (is (= {"deploy" {"1970-01-01" 800}}
           (sut/wait-times-by-day [(a-triggered-build "deploy" 1000 "test" "41")
                                   (a-build "test" "41" 200)]))))

  (testing "should handle optional 'end' value for triggering build"
    (is (empty? (sut/wait-times-by-day [(a-triggered-build "deploy" 1000 "test" "41")
                                        (-> (a-build "test" "41" 200)
                                            (dissoc :end))]))))

  (testing "should handle optional 'end' value for triggered build"
    (is (empty? (sut/wait-times-by-day [(-> (a-triggered-build "deploy" 1000 "test" "41")
                                            (dissoc :end))
                                        (a-build "test" "41" 200)]))))

  (testing "should handle missing triggering build"
    (is (empty? (sut/wait-times-by-day [(a-triggered-build "deploy" 1000 "test" "41")]))))

  (testing "should average wait times"
    (is (= {"deploy" {"1970-01-01" 900}}
           (sut/wait-times-by-day [(a-triggered-build "deploy" "42" 2000 "test" "41")
                                   (a-build "test" "41" 1000)
                                   (a-triggered-build "deploy" "30" 1000 "test" "30")
                                   (a-build "test" "30" 200)]))))

  (testing "should list wait times by day"
    (is (= {"deploy" {"1970-01-01" 800
                      "1970-01-02" 1000}}
           (sut/wait-times-by-day [(a-triggered-build "deploy" "42" (+ 2000 a-day) "test" "41")
                                   (a-build "test" "41" (+ 1000 a-day))
                                   (a-triggered-build "deploy" "30" 1000 "test" "30")
                                   (a-build "test" "30" 200)])))))
