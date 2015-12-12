(ns buildviz.controllers.flaky-testcases-test
  (:require [buildviz.test-utils :refer :all]
            [clj-time
             [coerce :as tc]
             [core :as t]]
            [clojure
             [string :as str]
             [test :refer :all]]))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))

(deftest FlakyTestCases
  (testing "GET to /flakytestcases as text/plain"
    (is (= 200
           (:status (plain-get-request (the-app) "/flakytestcases"))))
    (is (= "latestFailure,flakyCount,job,latestBuildId,testsuite,classname,name\n"
           (:body (plain-get-request (the-app) "/flakytestcases"))))
    (let [app (the-app
               {"aBuild" {"failing" {:outcome "fail" :inputs {:revision 1 :source_id 0} :start a-timestamp}
                          "passing" {:outcome "pass" :inputs {:revision 1 :source_id 0}}}
                "anotherBuild" {"failing" {:outcome "fail" :inputs {:revision "abcd" :source_id "100"} :start a-timestamp}
                                "passing" {:outcome "pass" :inputs {:revision "abcd" :source_id "100"}}}
                "buildWithoutTests" {"failing" {:outcome "fail" :inputs {:revision 0 :source_id 42}}
                                     "passing" {:outcome "pass" :inputs {:revision 0 :source_id 42}}}}
               {"aBuild" {"failing" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\"><failure/></testcase></testsuite></testsuite>"
                          "passing" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\"></testcase></testsuite></testsuite>"}
                "anotherBuild" {"failing" "<testsuite name=\"a suite\"><testcase name=\"testcase\" classname=\"class\"><failure/></testcase></testsuite>"
                                "passing" "<testsuite name=\"a suite\"><testcase name=\"testcase\" classname=\"class\"></testcase></testsuite>"}})]
      (is (= (str/join ["latestFailure,flakyCount,job,latestBuildId,testsuite,classname,name\n"
                    "1986-10-14 04:03:27,1,aBuild,failing,a suite: nested suite,class,testcase\n"
                    "1986-10-14 04:03:27,1,anotherBuild,failing,a suite,class,testcase\n"])
             (:body (plain-get-request app "/flakytestcases")))))))
