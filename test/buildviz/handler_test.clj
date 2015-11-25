(ns buildviz.handler-test
  (:require [buildviz.data.results :refer :all]
            [buildviz.handler :as handler]
            [cheshire.core :as json]
            [clj-time
             [coerce :as tc]
             [core :as t]]
            [clojure
             [string :as str]
             [test :refer :all]]
            [ring.mock.request :refer :all]))

(defn dummy-persist [_ _ _])

(defn- load-tests-from [mock-testresults]
  (fn [job-name build-id]
    (get-in @mock-testresults [job-name build-id])))

(def pipeline-name "Test Pipeline")

(defn the-app
  ([]
   (the-app {} {}))
  ([jobs testresults]
   (let [stored-testresults (atom testresults)
         persist-testresults (fn [job-name build-id xml]
                               (swap! stored-testresults assoc-in [job-name build-id] xml))]
     (handler/create-app (build-results jobs
                                        (load-tests-from stored-testresults)
                                        dummy-persist
                                        persist-testresults)
                         pipeline-name))))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))
(def a-day (* 24 60 60 1000))

;; helpers

(defn get-request
  ([app url]
   (get-request app url {}))
  ([app url query-params]
   (app (-> (request :get url)
            (query-string query-params)))))

(defn plain-get-request [app url]
  (app (-> (request :get url)
           (header :accept "text/plain"))))

(defn json-get-request
  ([app url]
   (json-get-request app url {}))
  ([app url query-params]
   (app (-> (request :get url)
            (query-string query-params)
            (header :accept "application/json")))))

(defn json-put-request [app url json]
  (app (-> (request :put url)
           (body (json/generate-string json))
           (content-type "application/json"))))

(defn xml-put-request [app url xml]
  (app (-> (request :put url)
           (body xml)
           (content-type "application/xml"))))

(defn json-body [response]
  (json/parse-string (:body response)))

;; test fixtures

(defn a-build [app job-name build-nr content]
  (json-put-request app
                    (format "/builds/%s/%s" job-name build-nr)
                    content))

(defn some-test-results [app job-name build-no content]
  (xml-put-request app
                   (format "/builds/%s/%s/testresults" job-name build-no)
                   content))

;; tests

(deftest Storage
  (testing "PUT to /builds/:job/:build"
    ;; PUT should return 200
    (is (= (:status (json-put-request (the-app) "/builds/abuild/1" {:start 42}))
           200))

    ;; PUT should return 400 for unknown parameters
    (is (= (:status (json-put-request (the-app) "/builds/abuild/1" {:unknown "value"}))
           400))

    ;; PUT should return 400 for illegal outcome
    (is (= (:status (json-put-request (the-app) "/builds/abuild/1" {:outcome "banana"}))
           400))

    ;; PUT should return content
    (let [response (json-put-request (the-app) "/builds/abuild/1" {:start 42 :end 43})
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43}))))

  (testing "GET to /builds/:job/:build"
    ;; GET should return 200
    (let [app (the-app)]
      (a-build app "anotherBuild" 1 {:start 42 :end 43})
      (is (= (:status (get-request app "/builds/anotherBuild/1"))
             200)))

    ;; GET should return content stored by PUT
    (let [app (the-app)]
      (a-build app "yetAnotherBuild" 1, {:start 42 :end 43})
      (is (= (json-body (get-request app "/builds/yetAnotherBuild/1"))
             {"start" 42 "end" 43})))

    ;; GET should return 404 if job not found
    (is (= (:status (get-request (the-app) "/builds/unknownBuild/10"))
           404))

    ;; GET should return 404 if build not found
    (let [app (the-app)]
      (a-build app "anExistingBuild" 1, {:start 42 :end 43})
      (is (= (:status (get-request app "/builds/anExistingBuild/2"))
             404)))

    ;; Different jobs should not interfere with each other
    (let [app (the-app)]
      (a-build app "someBuild" 1, {:start 42 :end 43})
      (is (= (:status (get-request app "/builds/totallyUnrelatedBuild/1"))
             404)))))


(deftest JUnitStorage
  (testing "PUT to /builds/:job/:build/testresults"
    (is (= 204 (:status (xml-put-request (the-app) "/builds/mybuild/1/testresults" "<testsuites></testsuites>"))))
    (is (= 400 (:status (xml-put-request (the-app) "/builds/mybuild/1/testresults" "not xml"))))
    (is (= 400 (:status (xml-put-request (the-app) "/builds/mybuild/1/testresults" "<testsuite name=\"suite\"><testcase classname=\"class\"/></testsuite>")))))

  (testing "GET to /builds/:job/:build/testresults"
    (let [app (the-app)]
      (some-test-results app "abuild" "42" "<testsuites></testsuites>")
      (let [response (get-request app "/builds/abuild/42/testresults")]
        (is (= 200
               (:status (get-request app "/builds/abuild/42/testresults"))))))

    (let [app (the-app)]
      (some-test-results app "anotherBuild" "2" "<testsuites></testsuites>")
      (is (= "<testsuites></testsuites>"
             (:body (get-request app "/builds/anotherBuild/2/testresults")))))

    (let [app (the-app)]
      (some-test-results app "anotherBuild" "2" "<testsuites></testsuites>")
      (is (= "application/xml;charset=UTF-8"
             (get (:headers (get-request app "/builds/anotherBuild/2/testresults"))
                  "Content-Type"))))

    (is (= 404
           (:status (get-request (the-app) "/builds/missingJob/1/testresults"))))

    (let [app (the-app)]
      (some-test-results app "jobMissingABuild" "1" "<testsuites></testsuites>")
      (is (= 404
             (:status (get-request app "/builds/jobMissingABuild/2/testresults")))))

    (let [app (the-app)]
      (some-test-results app "job" "1" "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"suite\"><testcase name=\"case\" classname=\"the class\"></testcase></testsuite>")
      (is (= [{"name" "suite"
               "children" [{"name" "case"
                            "classname" "the class"
                            "status" "pass"}]}]
             (json-body (json-get-request app "/builds/job/1/testresults")))))))

(deftest Status
  (testing "should return total number of builds and time of latest build"
    (let [app (the-app
               {"aBuild" {"1" {:start a-timestamp}
                          "2" {:start (+ a-timestamp (* 2 a-day))}}
                "anotherBuild" {"3" {:start (+ a-timestamp a-day)}}}
               {})
          body (json-body (json-get-request app "/status"))]
      (is (= 3
             (get body "totalBuildCount")))
      (is (= (+ a-timestamp (* 2 a-day))
             (get body "latestBuildStart")))))

  (testing "should handle a build without a start"
    (let [app (the-app
               {"aBuild" {"1" {:start 0}}}
               {})
          body (json-body (json-get-request app "/status"))]
      (is (= 1
             (get body "totalBuildCount")))))

  (testing "should handle no builds"
    (let [body (json-body (json-get-request (the-app) "/status"))]
      (is (= (get body "totalBuildCount")
             0))))

  (testing "should expose pipeline name"
    (let [body (json-body (json-get-request (the-app) "/status"))]
      (is (= pipeline-name
             (get body "pipelineName"))))))

(deftest JobsSummary

  (testing "GET to /jobs"
    ;; GET should return 200
    (is (= (:status (get-request (the-app) "/jobs"))
           200))

    ;; GET should return empty list
    (is (= (:body (plain-get-request (the-app) "/jobs"))
           "job,averageRuntime,totalCount,failedCount,flakyCount\n"))

    ;; GET should return job summary
    (let [app (the-app)]
      (a-build app "someBuild" 1, {:start 10 :end 20 :outcome "pass" :inputs [{:source_id 42 :revision "dat_revision"}]})
      (a-build app "someBuild" 2, {:start 30 :end 60 :outcome "fail" :inputs [{:source_id 42 :revision "dat_revision"}]})
      (a-build app "someBuild" 3, {:start 70 :end 90 :outcome "fail" :inputs [{:source_id 42 :revision "other_revision"}]})
      (a-build app "someBuild" 4, {:start 100 :end 120 :outcome "pass" :inputs [{:source_id 42 :revision "yet_another_revision"}]})
      (is (= (:body (plain-get-request app "/jobs"))
             (str/join ["job,averageRuntime,totalCount,failedCount,flakyCount\n"
                    (format "someBuild,%.8f,4,2,1\n" 0.00000023)]))))

    ;; GET should return empty map for json by default
    (is (= (json-body (json-get-request (the-app) "/jobs"))
           {}))

    ;; GET should return job summary
    (let [app (the-app)]
      (a-build app "someBuild" 1, {:start 42 :end 43})
      (a-build app "anotherBuild" 1, {:start 10 :end 12})
      (is (= (json-body (json-get-request app "/jobs"))
             {"someBuild" {"averageRuntime" 1 "totalCount" 1}
              "anotherBuild" {"averageRuntime" 2 "totalCount" 1}})))

    ;; GET should return total build count
    (let [app (the-app {"runOnce" {1 {:start 10}}
                        "runTwice" {1 {:start 20}
                                    2 {:start 30}}}
                       {})]
      (is (= (json-body (json-get-request app "/jobs"))
             {"runTwice" {"totalCount" 2}
              "runOnce" {"totalCount" 1}})))

    ;; GET should return failed build count
    (let [app (the-app {"flakyBuild" {1 {:outcome "pass" :start 10}
                                      2 {:outcome "fail" :start 20}}
                        "brokenBuild" {1 {:outcome "fail" :start 30}}}
                       {})]
      (is (= (json-body (json-get-request app "/jobs"))
             {"flakyBuild" {"failedCount" 1 "totalCount" 2 "flakyCount" 0}
              "brokenBuild" {"failedCount" 1 "totalCount" 1 "flakyCount" 0}})))

    ;; GET should return error build count
    (let [app (the-app {"goodBuild" {1 {:outcome "pass" :start 10}}}
                       {})]
      (is (= (json-body (json-get-request app "/jobs"))
             {"goodBuild" {"failedCount" 0 "totalCount" 1 "flakyCount" 0}})))

    ;; GET should return a flaky build count
    (let [app (the-app {"flakyBuild" {1 {:outcome "pass" :inputs [{:source_id 42 :revision "dat_revision"}] :start 10}
                                      2 {:outcome "fail" :inputs [{:source_id 42 :revision "dat_revision"}] :start 20}}}
                       {})]
      (is (= (json-body (json-get-request app "/jobs"))
             {"flakyBuild" {"failedCount" 1 "totalCount" 2 "flakyCount" 1}}))))

  (testing "should respect 'from' filter"
    (let [app (the-app
               {"aBuild" {"1" {:start 42}
                          "2" {:start 43}}}
               {})]
      (is (= {"aBuild" {"totalCount" 1}}
             (json-body (json-get-request app "/jobs" {"from" 43})))))))

(deftest PipelineRuntimeSummary
  (testing "GET to /pipelineruntime"
    ;; GET should return 200
    (is (= 200
           (:status (get-request (the-app) "/pipelineruntime"))))

    ;; GET should return empty list by default
    (is (= "date\n"
           (:body (get-request (the-app) "/pipelineruntime"))))

    ;; GET should return the average runtime for each job as well as total
    (let [app (the-app)]
      (a-build app "aBuild" 1, {:start a-timestamp :end (+ a-timestamp 1000)})
      (a-build app "aBuild" 2, {:start (+ a-timestamp 2000) :end (+ a-timestamp 4001)})
      (a-build app "aBuild" 3, {:start (+ a-timestamp a-day) :end (+ a-timestamp a-day 4000)})
      (a-build app "anotherBuild" 1, {:start a-timestamp :end (+ a-timestamp 4000)})
      (a-build app "buildWithoutTimestamps" 1, {:outcome "pass"})
      (is (= (str/join "\n" ["date,aBuild,anotherBuild"
                         (format "1986-10-14,%.8f,%.8f" 0.00001737 0.00004630)
                         (format "1986-10-15,%.8f," 0.00004630)
                         ""])
             (:body (get-request app "/pipelineruntime"))))))

  (testing "should respect 'from' filter"
    (let [app (the-app {"aBuild" {1 {:start (- a-timestamp a-day), :end (- a-timestamp a-day)}}
                        "anotherBuild" {1 {:start a-timestamp :end a-timestamp}}}
                       {})]
      (is (= (str/join "\n" ["date,anotherBuild"
                         (format "1986-10-14,%.8f" 0.0)
                         ""])
             (:body (get-request app "/pipelineruntime" {"from" a-timestamp})))))))

(deftest FailPhases
  (testing "GET to /failphases"
    ;; GET should return 200
    (is (= 200
           (:status (get-request (the-app) "/failphases"))))

    ;; GET should return empty list by default
    (is (= "start,end,culprits\n"
           (:body (get-request (the-app) "/failphases"))))

    ;; GET should return fail phases
    (let [app (the-app {"badBuild" {1 {:start 0 :end a-timestamp :outcome "fail"}
                                    2 {:start 0 :end (+ a-timestamp 30000) :outcome "pass"}}
                        "anotherBuild" {1 {:start 0 :end (+ a-timestamp 10000) :outcome "fail"}
                                        2 {:start 0 :end (+ a-timestamp 20000) :outcome "pass"}}}
                       {})]
      (is (= "start,end,culprits\n1986-10-14 04:03:27,1986-10-14 04:03:57,anotherBuild|badBuild\n"
             (:body (get-request app "/failphases")))))

    ;; GET should return empty list by default as JSON
    (is (= []
           (json-body (json-get-request (the-app) "/failphases"))))

    ;; GET should return fail phases as JSON
    (let [app (the-app {"badBuild" {1 {:start 0 :end 42 :outcome "fail"}
                                    2 {:start 70 :end 80 :outcome "pass"}}}
                       {})]
      (is (= [{"start" 42 "end" 80 "culprits" ["badBuild"]}]
             (json-body (json-get-request app "/failphases"))))))

  (testing "should respect 'from' filter"
    (let [app (the-app)]
      (a-build app "badBuild" 1, {:start 20 :end 42 :outcome "fail"})
      (a-build app "badBuild" 2, {:start 70 :end 80 :outcome "pass"})
      (a-build app "badBuild" 3, {:start 90 :end 100 :outcome "fail"})
      (a-build app "badBuild" 4, {:start 190 :end 200 :outcome "pass"})
      (is (= [{"start" 100 "end" 200 "culprits" ["badBuild"]}]
           (json-body (json-get-request app "/failphases" {"from" 60})))))))


(deftest FailuresSummary
  (testing "GET to /failures"
    ;; GET should return 200
    (is (= 200
           (:status (get-request (the-app) "/failures"))))

    ;; GET should return an empty list in CSV
    (is (= "failedCount,job,testsuite,classname,name\n"
           (:body (get-request (the-app) "/failures"))))

    ;; GET should include a list of failing test cases
    (let [app (the-app {"failingBuild" {1 {:outcome "fail" :start 10}
                                        2 {:outcome "fail" :start 20}}
                        "anotherFailingBuild" {1 {:outcome "fail" :start 30}}
                        "failingBuildWithoutTestResults" {1 {:outcome "fail" :start 40}}
                        "passingBuild" {1 {:outcome "pass" :start 50}}}
                       {"failingBuild" {1 "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\" classname=\"a class\"><failure/></testcase></testsuite></testsuites>"
                                        2 "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\" classname=\"a class\"><failure/></testcase></testsuite></testsuites>"}
                        "anotherFailingBuild" {1 "<testsuites><testsuite name=\"another suite\"><testsuite name=\"nested suite\"><testcase name=\"another test\" classname=\"some class\"><failure/></testcase></testsuite></testsuite></testsuites>"}
                        "passingBuild" {1 "<testsuites><testsuite name=\"suite\"><testcase name=\"test\" classname=\"class\"></testcase></testsuite></testsuites>"}})]
      (is (= (str/join "\n" ["failedCount,job,testsuite,classname,name"
                         "1,anotherFailingBuild,another suite: nested suite,some class,another test"
                         "2,failingBuild,a suite,a class,a test"
                         ""])
             (:body (get-request app "/failures")))))

    ;; GET should return empty map by default for JSON
    (is (= {}
           (json-body (json-get-request (the-app) "/failures"))))

    ;; GET should include a list of failing test cases for JSON
    (let [app (the-app {"failingBuild" {1 {:outcome "fail" :start 0}}
                        "anotherFailingBuild" {1 {:outcome "fail" :start 0}}
                        "failingBuildWithoutTestResults" {1 {:outcome "fail" :start 0}}
                        "passingBuild" {1 {:outcome "pass" :start 0}}}
                       {"failingBuild" {1 "<testsuites><testsuite name=\"a suite\"><testcase classname=\"class\" name=\"a test\"><failure/></testcase></testsuite></testsuites>"}
                        "anotherFailingBuild" {1 "<testsuites><testsuite name=\"another suite\"><testcase classname=\"class\" name=\"another test\"><failure/></testcase></testsuite></testsuites>"}
                        "passingBuild" {1 "<testsuites><testsuite name=\"suite\"><testcase classname=\"class\" name=\"test\"></testcase></testsuite></testsuites>"}})]
      (is (= {"failingBuild" {"failedCount" 1 "children" [{"name" "a suite"
                                                           "children" [{"name" "class"
                                                                        "children" [{"name" "a test"
                                                                                     "failedCount" 1}]}]}]}
              "anotherFailingBuild" {"failedCount" 1 "children" [{"name" "another suite"
                                                                  "children" [{"name" "class"
                                                                               "children" [{"name" "another test"
                                                                                            "failedCount" 1}]}]}]}}
             (json-body (json-get-request app "/failures")))))
    )

  (testing "should filter by start date"
    (let [app (the-app {"aBuild" {"1" {:start 47 :outcome "fail"} "2" {:start 50 :outcome "fail"}}}
                       {"aBuild" {"1" "<testsuite name=\"suite\"><testcase classname=\"c\" name=\"t\"><failure/></testcase></testsuite>"
                                  "2" "<testsuite name=\"suite\"><testcase classname=\"c\" name=\"t\"><failure/></testcase></testsuite>"}})]
      (is (= {"aBuild" {"failedCount" 1
                        "children" [{"name" "suite"
                                     "children" [{"name" "c"
                                                  "children" [{"name" "t"
                                                               "failedCount" 1}]}]}]}}
             (json-body (json-get-request app "/failures" {"from" 50})))))))

(deftest TestCasesSummary
  (testing "GET to /testcases"
    ;; GET should return 200
    (is (= 200
           (:status (json-get-request (the-app) "/testcases"))))

    ;; GET should return empty map by default
    (is (= {}
           (json-body (json-get-request (the-app) "/testcases"))))

    ;; GET should include a list of builds with test cases
    (let [app (the-app {"aBuild" {1 {:start 0}
                                  2 {:start 1}}}
                       {"aBuild" {1 "<testsuites><testsuite name=\"a suite\"><testcase classname=\"class\" name=\"a test\" time=\"10\"></testcase></testsuite></testsuites>"
                                  2 "<testsuites><testsuite name=\"a suite\"><testcase classname=\"class\" name=\"a test\" time=\"30\"></testcase></testsuite></testsuites>"}})]
      (is (= {"aBuild" {"children" [{"name" "a suite"
                                     "children" [{"name" "class"
                                                  "children" [{"name" "a test"
                                                               "averageRuntime" 20000}]}]}]}}
             (json-body (json-get-request app "/testcases")))))

    ;; GET should return CSV by default
    (let [app (the-app {"aBuild" {1 {:start 0}}}
                       {"aBuild" {1 "<testsuites><testsuite name=\"a suite\"><testcase name=\"a,test\" classname=\"a class\" time=\"10\"></testcase></testsuite></testsuites>"}})]
      (is (= (:body (get-request app "/testcases"))
             (str/join ["averageRuntime,job,testsuite,classname,name\n"
                    (format "%.8f,aBuild,a suite,a class,\"a,test\"\n" 0.00011574)]))))

    ;; GET should handle nested testsuites in CSV
    (let [app (the-app {"aBuild" {1 {:start 0}}}
                       {"aBuild" {1 "<testsuites><testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"a,test\" classname=\"a class\" time=\"10\"></testcase></testsuite></testsuite></testsuites>"}})]
      (is (= (:body (plain-get-request app "/testcases"))
             (str/join ["averageRuntime,job,testsuite,classname,name\n"
                    (format "%.8f,aBuild,a suite: nested suite,a class,\"a,test\"\n" 0.00011574)]))))

    ;; GET should not include builds without test cases
    (let [app (the-app {"aBuild" {1 {:start 0}}}
                       {})]
      (is (= {}
             (json-body (json-get-request app "/testcases")))))
    ))

(deftest TestClassesSummary
  (testing "GET to /testclasses as application/json"
    (is (= 200
           (:status (json-get-request (the-app) "/testclasses"))))
    (is (= {}
           (json-body (json-get-request (the-app) "/testclasses"))))
    (let [app (the-app
               {"aBuild" {"1" {:start 0}
                          "2" {:start 1}}}
               {"aBuild" {"1" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\" time=\"10\"/><testcase name=\"another testcase\" classname=\"class\" time=\"30\"/></testsuite></testsuite>"
                          "2" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\" time=\"60\"/></testsuite></testsuite>"}})]
      (is (= {"aBuild" {"children" [{"name" "a suite"
                                     "children" [{"name" "nested suite"
                                                  "children" [{"name" "class"
                                                               "averageRuntime" 50000}]}]}]}}
             (json-body (json-get-request app "/testclasses"))))

      ;; GET should not include builds without test cases
      (let [app (the-app
                 {"aBuild" {"1" {:start 0}}}
                 {})]
        (is (= {}
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
                    (format "%.8f,aBuild,a suite: nested suite,class\n" 0.0005787)])
             (:body (plain-get-request app "/testclasses")))))

    ;; GET should not include builds without test cases
    (let [app (the-app
               {"aBuild" {"1" {:start 0}}}
               {})]
      (is (= "averageRuntime,job,testsuite,classname\n"
             (:body (plain-get-request app "/testclasses")))))))

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
               {"aBuild" {"failing" "<testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"testcase\" classname=\"class\"><failure/></testcase></testsuite></testsuite>"}
                "anotherBuild" {"failing" "<testsuite name=\"a suite\"><testcase name=\"testcase\" classname=\"class\"><failure/></testcase></testsuite>"}})]
      (is (= (str/join ["latestFailure,flakyCount,job,latestBuildId,testsuite,classname,name\n"
                    "1986-10-14 04:03:27,1,aBuild,failing,a suite: nested suite,class,testcase\n"
                    "1986-10-14 04:03:27,1,anotherBuild,failing,a suite,class,testcase\n"])
             (:body (plain-get-request app "/flakytestcases")))))))

(deftest EntryPoint
  (testing "GET to /"
    (is (= 302
           (:status (get-request (the-app) "/"))))
    (is (= "/index.html"
           (get (:headers (get-request (the-app) "/")) "Location")))))
