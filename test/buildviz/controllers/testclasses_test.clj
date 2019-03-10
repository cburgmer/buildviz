(ns buildviz.controllers.testclasses-test
  (:require [buildviz.test-utils :refer :all]
            [clojure
             [string :as str]
             [test :refer :all]]))

(deftest TestClassesSummary
  (testing "GET to /testclasses as application/json"
    (is (= 200
           (:status (json-get-request (the-app) "/testclasses"))))
    (is (= []
           (json-body (json-get-request (the-app) "/testclasses"))))
    (let [app (the-app
               {"aBuild" {"1" {:start 0}
                          "2" {:start 1}}}
               {"aBuild" {"1" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\" time=\"10\"/><testcase name=\"another testcase\" classname=\"class\" time=\"30\"/></testsuite></testsuite>"
                          "2" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\" time=\"60\"/></testsuite></testsuite>"}})]
      (is (= [{"jobName" "aBuild"
               "children" [{"name" "a suite"
                            "children" [{"name" "nested suite"
                                         "children" [{"name" "class"
                                                      "averageRuntime" 50000}]}]}]}]
             (json-body (json-get-request app "/testclasses"))))

      ;; GET should not include builds without test cases
      (let [app (the-app
                 {"aBuild" {"1" {:start 0}}}
                 {})]
        (is (= []
               (json-body (json-get-request app "/testclasses")))))))

  (testing "GET to /testclasses as text/plain"
    (is (= "averageRuntime,job,testsuite,classname\n"
           (:body (plain-get-request (the-app) "/testclasses"))))
    (let [app (the-app
               {"aBuild" {"1" {:start 0}
                          "2" {:start 1}}}
               {"aBuild" {"1" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\" time=\"10\"/><testcase name=\"another testcase\" classname=\"class\" time=\"30\"/></testsuite></testsuite>"
                          "2" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\" time=\"60\"/></testsuite></testsuite>"}})]
      (is (= (str/join ["averageRuntime,job,testsuite,classname\n"
                        "0.00057870,aBuild,a suite: nested suite,class\n"])
             (:body (plain-get-request app "/testclasses")))))

    ;; GET should not include builds without test cases
    (let [app (the-app
               {"aBuild" {"1" {:start 0}}}
               {})]
      (is (= "averageRuntime,job,testsuite,classname\n"
             (:body (plain-get-request app "/testclasses")))))))
