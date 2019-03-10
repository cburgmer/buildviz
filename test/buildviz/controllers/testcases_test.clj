(ns buildviz.controllers.testcases-test
  (:require [buildviz.test-utils :refer :all]
            [clojure
             [string :as str]
             [test :refer :all]]))

(deftest TestCasesSummary
  (testing "GET to /testcases"
    ;; GET should return 200
    (is (= 200
           (:status (json-get-request (the-app) "/testcases")))))

  (testing "GET should return empty map by default"
    (is (= []
           (json-body (json-get-request (the-app) "/testcases")))))

  (testing "GET should include a list of builds with test cases"
    (let [app (the-app {"aBuild" {1 {:start 0}
                                  2 {:start 1}}}
                       {"aBuild" {1 "<testsuites><testsuite name=\"a suite\"><testcase classname=\"class\" name=\"a test\" time=\"10\"></testcase></testsuite></testsuites>"
                                  2 "<testsuites><testsuite name=\"a suite\"><testcase classname=\"class\" name=\"a test\" time=\"30\"></testcase></testsuite></testsuites>"}})]
      (is (= [{"jobName" "aBuild"
               "children" [{"name" "a suite"
                            "children" [{"name" "class"
                                         "children" [{"name" "a test"
                                                      "averageRuntime" 20000
                                                      "failedCount" 0}]}]}]}]
             (json-body (json-get-request app "/testcases"))))))

  (testing "GET should return CSV by default"
    (let [app (the-app {"aBuild" {1 {:start 0}}}
                       {"aBuild" {1 "<testsuites><testsuite name=\"a suite\"><testcase name=\"a,test\" classname=\"a class\" time=\"10\"></testcase></testsuite></testsuites>"}})]
      (is (= (str/join ["averageRuntime,failedCount,job,testsuite,classname,name\n"
                        "0.00011574,0,aBuild,a suite,a class,\"a,test\"\n"])
             (:body (get-request app "/testcases"))))))

  (testing "GET should handle nested testsuites in CSV"
    (let [app (the-app {"aBuild" {1 {:start 0}}}
                       {"aBuild" {1 "<testsuites><testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"a,test\" classname=\"a class\" time=\"10\"><failure/></testcase></testsuite></testsuite></testsuites>"}})]
      (is (= (str/join ["averageRuntime,failedCount,job,testsuite,classname,name\n"
                        "0.00011574,1,aBuild,a suite: nested suite,a class,\"a,test\"\n"])
             (:body (plain-get-request app "/testcases"))))))

  (testing "GET should not include builds without test cases"
    (let [app (the-app {"aBuild" {1 {:start 0}}}
                       {})]
      (is (= []
             (json-body (json-get-request app "/testcases"))))))

  (testing "should handle missing runtime"
    (let [app (the-app {"aBuild" {1 {:start 0}}}
                       {"aBuild" {1 "<testsuites><testsuite name=\"a suite\"><testcase name=\"a,test\" classname=\"a class\"></testcase></testsuite></testsuites>"}})]
      (is (= (str/join ["averageRuntime,failedCount,job,testsuite,classname,name\n"
                        ",0,aBuild,a suite,a class,\"a,test\"\n"])
             (:body (get-request app "/testcases"))))))

  (testing "should filter by start date"
    (let [app (the-app {"aBuild" {"1" {:start 47} "2" {:start 50}}}
                       {"aBuild" {"1" "<testsuite name=\"suite\"><testcase classname=\"c\" name=\"t\"><failure/></testcase></testsuite>"
                                  "2" "<testsuite name=\"suite\"><testcase classname=\"c\" name=\"t\"><failure/></testcase></testsuite>"}})]
      (is (= [{"jobName" "aBuild"
               "children" [{"name" "suite"
                            "children" [{"name" "c"
                                         "children" [{"name" "t"
                                                      "failedCount" 1}]}]}]}]
             (json-body (json-get-request app "/testcases" {"from" 50})))))))
