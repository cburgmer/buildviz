(ns buildviz.controllers.jobs-test
  (:require [buildviz.test-utils :refer :all]
            [clojure
             [string :as str]
             [test :refer :all]]))

(deftest JobsSummary

  (testing "GET to /jobs"
    ;; GET should return 200
    (is (= (:status (get-request (the-app) "/jobs"))
           200))

    ;; GET should return empty list
    (is (= (:body (plain-get-request (the-app) "/jobs"))
           "job,averageRuntime,totalCount,failedCount,flakyCount\n"))

    ;; GET should return job summary
    (let [app (the-app {"someBuild" {1 {:start 10 :end 20 :outcome "pass" :inputs [{:source-id 42 :revision "dat_revision"}]}
                                     2 {:start 30 :end 60 :outcome "fail" :inputs [{:source-id 42 :revision "dat_revision"}]}
                                     3 {:start 70 :end 90 :outcome "fail" :inputs [{:source-id 42 :revision "other_revision"}]}
                                     4 {:start 100 :end 120 :outcome "pass" :inputs [{:source-id 42 :revision "yet_another_revision"}]}}}
                       {})]
      (is (= (:body (plain-get-request app "/jobs"))
             (str/join ["job,averageRuntime,totalCount,failedCount,flakyCount\n"
                    (format "someBuild,%.8f,4,2,1\n" 0.00000023)]))))

    ;; GET should return empty map for json by default
    (is (= []
           (json-body (json-get-request (the-app) "/jobs"))))

    ;; GET should return job summary
    (let [app (the-app)]
      (a-build app "someBuild" 1, {:start 42 :end 43})
      (a-build app "anotherBuild" 1, {:start 10 :end 12})
      (is (= [{"jobName" "anotherBuild"
               "averageRuntime" 2
               "totalCount" 1}
              {"jobName" "someBuild"
               "averageRuntime" 1
               "totalCount" 1}]
             (json-body (json-get-request app "/jobs")))))

    ;; GET should return total build count
    (let [app (the-app {"runOnce" {1 {:start 10}}
                        "runTwice" {1 {:start 20}
                                    2 {:start 30}}}
                       {})]
      (is (= [{"jobName" "runOnce"
               "totalCount" 1}
              {"jobName" "runTwice"
               "totalCount" 2}]
             (json-body (json-get-request app "/jobs")))))

    ;; GET should return failed build count
    (let [app (the-app {"flakyBuild" {1 {:outcome "pass" :start 10}
                                      2 {:outcome "fail" :start 20}}
                        "brokenBuild" {1 {:outcome "fail" :start 30}}}
                       {})]
      (is (= [{"jobName" "brokenBuild"
               "failedCount" 1
               "totalCount" 1
               "flakyCount" 0}
              {"jobName" "flakyBuild"
               "failedCount" 1
               "totalCount" 2
               "flakyCount" 0}]
             (json-body (json-get-request app "/jobs")))))

    ;; GET should return error build count
    (let [app (the-app {"goodBuild" {1 {:outcome "pass" :start 10}}}
                       {})]
      (is (= [{"jobName" "goodBuild"
               "failedCount" 0
               "totalCount" 1
               "flakyCount" 0}]
             (json-body (json-get-request app "/jobs")))))

    ;; GET should return a flaky build count
    (let [app (the-app {"flakyBuild" {1 {:outcome "pass" :inputs [{:source-id 42 :revision "dat_revision"}] :start 10}
                                      2 {:outcome "fail" :inputs [{:source-id 42 :revision "dat_revision"}] :start 20}}}
                       {})]
      (is (= [{"jobName" "flakyBuild"
               "failedCount" 1
               "totalCount" 2
               "flakyCount" 1}]
             (json-body (json-get-request app "/jobs"))))))

  (testing "should respect 'from' filter"
    (let [app (the-app
               {"aBuild" {"1" {:start 42}
                          "2" {:start 43}}}
               {})]
      (is (= [{"jobName" "aBuild"
               "totalCount" 1}]
             (json-body (json-get-request app "/jobs" {"from" 43})))))))
