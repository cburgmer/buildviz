(ns buildviz.handler-test
  (:use clojure.test
        ring.mock.request
        [buildviz.build-results :only (build-results)]
        [clojure.string :only (join)])
  (:require [cheshire.core :as json]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [buildviz.handler :as handler]))


(def builds (atom {}))
(def tests (atom {}))

(defn dummy-persist [_])

(def app (handler/create-app (build-results builds tests) dummy-persist dummy-persist))

(defn- reset-app! []
  (reset! builds {})
  (reset! tests {}))

(defn each-fixture [f]
  (reset-app!)
  (f))

(use-fixtures :each each-fixture)

;; helpers

(defn get-request [app url]
  (app (request :get url)))

(defn plain-get-request [app url]
  (app (-> (request :get url)
           (header :accept "text/plain"))))

(defn json-get-request [app url]
  (app (-> (request :get url)
           (header :accept "application/json"))))

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

(defn a-build [jobName buildNr content]
  (json-put-request app
                    (format "/builds/%s/%s" jobName buildNr)
                    content))

(defn some-test-results [job-name build-no content]
  (xml-put-request app
                   (format "/builds/%s/%s/testresults" job-name build-no)
                   content))

(deftest Storage
  (testing "PUT to /builds/:job/:build"
    ;; PUT should return 200
    (is (= (:status (json-put-request app "/builds/abuild/1" {}))
           200))

    ;; PUT should return 400 for unknown parameters
    (is (= (:status (json-put-request app "/builds/abuild/1" {:unknown "value"}))
           400))

    ;; PUT should return 400 for illegal outcome
    (is (= (:status (json-put-request app "/builds/abuild/1" {:outcome "banana"}))
           400))

    ;; PUT should return content
    (let [response (json-put-request app "/builds/abuild/1" {:start 42 :end 43})
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43}))))

  (testing "GET to /builds/:job/:build"
    ;; GET should return 200
    (a-build "anotherBuild" 1 {:start 42 :end 43})
    (let [response (get-request app "/builds/anotherBuild/1")]
      (is (= (:status response) 200)))

    ;; GET should return content stored by PUT
    (a-build "yetAnotherBuild" 1, {:start 42 :end 43})
    (is (= (json-body (get-request app "/builds/yetAnotherBuild/1"))
           {"start" 42 "end" 43}))

    ;; GET should return 404 if job not found
    (is (= (:status (get-request app "/builds/unknownBuild/10"))
           404))

    ;; GET should return 404 if build not found
    (a-build "anExistingBuild" 1, {:start 42 :end 43})
    (is (= (:status (get-request app "/builds/anExistingBuild/2"))
           404))

    ;; Different jobs should not interfere with each other
    (a-build "someBuild" 1, {:start 42 :end 43})
    (is (= (:status (get-request app "/builds/totallyUnrelatedBuild/1"))
           404))))


(deftest JUnitStorage
  (testing "PUT to /builds/:job/:build/testresults"
    (is (= 204 (:status (xml-put-request app "/builds/mybuild/1/testresults" "<testsuites></testsuites>"))))
    (is (= 400 (:status (xml-put-request app "/builds/mybuild/1/testresults" "not xml")))))

  (testing "GET to /builds/:job/:build/testresults"
    (some-test-results "abuild" "42" "<testsuites></testsuites>")
    (let [response (get-request app "/builds/abuild/42/testresults")]
      (is (= 200 (:status response))))

    (some-test-results "anotherBuild" "2" "<testsuites></testsuites>")
    (let [response (get-request app "/builds/anotherBuild/2/testresults")]
      (is (= "<testsuites></testsuites>" (:body response))))

    (some-test-results "anotherBuild" "2" "<testsuites></testsuites>")
    (let [response (get-request app "/builds/anotherBuild/2/testresults")]
      (is (= {"Content-Type" "application/xml;charset=UTF-8"} (:headers response))))

    (let [response (get-request app "/builds/missingJob/1/testresults")]
      (is (= 404 (:status response))))

    (some-test-results "jobMissingABuild" "1" "<testsuites></testsuites>")
    (let [response (get-request app "/builds/jobMissingABuild/2/testresults")]
      (is (= 404 (:status response))))

    (some-test-results "job" "1" "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"suite\"><testcase name=\"case\"></testcase></testsuite>")
    (let [response (json-get-request app "/builds/job/1/testresults")]
      (is (= [{"name" "suite"
               "children" [{"name" "case"
                            "status" "pass"}]}] (json/parse-string (:body response)))))
    ))


(deftest JobsSummary
  (testing "GET to /jobs"
    ;; GET should return 200
    (let [response (get-request app "/jobs")]
      (is (= (:status response) 200)))

    ;; GET should return empty list
    (let [response (plain-get-request app "/jobs")]
      (is (= (:body response)
             "job,averageRuntime,totalCount,failedCount,flakyCount\n")))

    ;; GET should return job summary
    (reset-app!)
    (a-build "someBuild" 1, {:start 10 :end 20 :outcome "pass" :inputs [{:id 42 :revision "dat_revision"}]})
    (a-build "someBuild" 2, {:start 30 :end 60 :outcome "fail" :inputs [{:id 42 :revision "dat_revision"}]})
    (a-build "someBuild" 3, {:start 70 :end 90 :outcome "fail" :inputs [{:id 42 :revision "other_revision"}]})
    (a-build "someBuild" 4, {:start 100 :end 120 :outcome "pass" :inputs [{:id 42 :revision "yet_another_revision"}]})
    (let [response (plain-get-request app "/jobs")]
      (is (= (:body response)
             (join ["job,averageRuntime,totalCount,failedCount,flakyCount\n"
                    (format "someBuild,%.8f,4,2,1\n" 0.00000023)]))))

    ;; GET should return empty map for json by default
    (reset-app!)
    (let [response (json-get-request app "/jobs")
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {})))

    ;; GET should return job summary
    (reset-app!)
    (a-build "someBuild" 1, {:start 42 :end 43})
    (a-build "anotherBuild" 1, {:start 10 :end 12})
    (is (= (json-body (json-get-request app "/jobs"))
           {"someBuild" {"averageRuntime" 1 "totalCount" 1}
            "anotherBuild" {"averageRuntime" 2 "totalCount" 1}}))

    ;; GET should return total build count
    (reset-app!)
    (a-build "runOnce" 1, {})
    (a-build "runTwice" 1, {})
    (a-build "runTwice" 2, {})
    (let [response (json-get-request app "/jobs")
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"runTwice" {"totalCount" 2}
                        "runOnce" {"totalCount" 1}})))

    ;; GET should return failed build count
    (reset-app!)
    (a-build "flakyBuild" 1, {:outcome "pass"})
    (a-build "flakyBuild" 2, {:outcome "fail"})
    (a-build "brokenBuild" 1, {:outcome "fail"})
    (let [response (json-get-request app "/jobs")
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"flakyBuild" {"failedCount" 1 "totalCount" 2 "flakyCount" 0}
                        "brokenBuild" {"failedCount" 1 "totalCount" 1 "flakyCount" 0}})))

    ;; GET should return error build count
    (reset-app!)
    (a-build "goodBuild" 1, {:outcome "pass"})
    (let [response (json-get-request app "/jobs")
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"goodBuild" {"failedCount" 0 "totalCount" 1 "flakyCount" 0}})))

    ;; GET should return a flaky build count
    (reset-app!)
    (a-build "flakyBuild" 1, {:outcome "pass" :inputs [{:id 42 :revision "dat_revision"}]})
    (a-build "flakyBuild" 2, {:outcome "fail" :inputs [{:id 42 :revision "dat_revision"}]})
    (let [response (json-get-request app "/jobs")
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"flakyBuild" {"failedCount" 1 "totalCount" 2 "flakyCount" 1}})))
    ))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))
(def a-day (* 24 60 60 1000))

(deftest PipelineRuntimeSummary
  (testing "GET to /pipelineruntime"
    ;; GET should return 200
    (let [response (get-request app "/pipelineruntime")]
      (is (= 200 (:status response))))

    ;; GET should return empty list by default
    (reset-app!)
    (let [response (get-request app "/pipelineruntime")
          resp-data (:body response)]
      (is (= "date\n" resp-data)))

    ;; GET should return the average runtime for each job as well as total
    (reset-app!)
    (a-build "aBuild" 1, {:start a-timestamp :end (+ a-timestamp 1000)})
    (a-build "aBuild" 2, {:start (+ a-timestamp 2000) :end (+ a-timestamp 4001)})
    (a-build "aBuild" 3, {:start (+ a-timestamp a-day) :end (+ a-timestamp a-day 4000)})
    (a-build "anotherBuild" 1, {:start a-timestamp :end (+ a-timestamp 4000)})
    (a-build "buildWithoutTimestamps" 1, {:outcome "pass"})
    (let [response (get-request app "/pipelineruntime")
          resp-data (:body response)]
      (is (= (join "\n" ["date,aBuild,anotherBuild"
                         (format "1986-10-14,%.8f,%.8f" 0.00001737 0.00004630)
                         (format "1986-10-15,%.8f," 0.00004630)
                         ""])
             resp-data)))
    ))

(deftest FailPhases
  (testing "GET to /failphases"
    ;; GET should return 200
    (let [response (get-request app "/failphases")]
      (is (= 200 (:status response))))

    ;; GET should return empty list by default
    (reset-app!)
    (let [response (get-request app "/failphases")
          resp-data (:body response)]
      (is (= "start,end,culprits\n" resp-data)))

    ;; GET should return fail phases
    (reset-app!)
    (a-build "badBuild" 1, {:end a-timestamp :outcome "fail"})
    (a-build "anotherBuild" 1, {:end (+ a-timestamp 10000) :outcome "fail"})
    (a-build "anotherBuild" 2, {:end (+ a-timestamp 20000) :outcome "pass"})
    (a-build "badBuild" 2, {:end (+ a-timestamp 30000) :outcome "pass"})
    (let [response (get-request app "/failphases")
          resp-data (:body response)]
      (is (= "start,end,culprits\n1986-10-14 04:03:27,1986-10-14 04:03:57,anotherBuild|badBuild\n" resp-data)))

    ;; GET should return empty list by default as JSON
    (reset-app!)
    (let [response (json-get-request app "/failphases")
          resp-data (json/parse-string (:body response))]
      (is (= [] resp-data)))

    ;; GET should return fail phases as JSON
    (reset-app!)
    (a-build "badBuild" 1, {:end 42 :outcome "fail"})
    (a-build "badBuild" 2, {:end 80 :outcome "pass"})
    (let [response (json-get-request app "/failphases")
          resp-data (json/parse-string (:body response))]
      (is (= [{"start" 42 "end" 80 "culprits" ["badBuild"]}] resp-data)))
    ))

(deftest FailuresSummary
  (testing "GET to /failures"
    ;; GET should return 200
    (let [response (get-request app "/failures")]
      (is (= 200 (:status response))))

    ;; GET should return an empty list in CSV
    (reset-app!)
    (let [response (get-request app "/failures")]
      (is (= "failedCount,job,testsuite,classname,name\n"
             (:body response))))

    ;; GET should include a list of failing test cases
    (reset-app!)
    (a-build "failingBuild" 1, {:outcome "fail"})
    (some-test-results "failingBuild" "1" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\" classname=\"a class\"><failure/></testcase></testsuite></testsuites>")
    (a-build "failingBuild" 2, {:outcome "fail"})
    (some-test-results "failingBuild" "2" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\" classname=\"a class\"><failure/></testcase></testsuite></testsuites>")
    (a-build "anotherFailingBuild" 1, {:outcome "fail"})
    (some-test-results "anotherFailingBuild" "1" "<testsuites><testsuite name=\"another suite\"><testsuite name=\"nested suite\"><testcase name=\"another test\" classname=\"some class\"><failure/></testcase></testsuite></testsuite></testsuites>")
    (a-build "failingBuildWithoutTestResults" 1, {:outcome "fail"})
    (a-build "passingBuild" 1, {:outcome "pass"})
    (some-test-results "passingBuild" "1" "<testsuites><testsuite name=\"suite\"><testcase name=\"test\" classname=\"class\"></testcase></testsuite></testsuites>")
    (let [response (get-request app "/failures")]
      (is (= (join "\n" ["failedCount,job,testsuite,classname,name"
                         "1,anotherFailingBuild,another suite: nested suite,some class,another test"
                         "2,failingBuild,a suite,a class,a test"
                         ""])
             (:body response))))

    ;; GET should return empty map by default for JSON
    (reset-app!)
    (let [response (json-get-request app "/failures")
          resp-data (json/parse-string (:body response))]
      (is (= {} resp-data)))

    ;; GET should include a list of failing test cases for JSON
    (reset-app!)
    (a-build "failingBuild" 1, {:outcome "fail"})
    (some-test-results "failingBuild" "1" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\"><failure/></testcase></testsuite></testsuites>")
    (a-build "anotherFailingBuild" 1, {:outcome "fail"})
    (some-test-results "anotherFailingBuild" "1" "<testsuites><testsuite name=\"another suite\"><testcase name=\"another test\"><failure/></testcase></testsuite></testsuites>")
    (a-build "failingBuildWithoutTestResults" 1, {:outcome "fail"})
    (a-build "passingBuild" 1, {:outcome "pass"})
    (some-test-results "passingBuild" "1" "<testsuites><testsuite name=\"suite\"><testcase name=\"test\"></testcase></testsuite></testsuites>")
    (let [response (json-get-request app "/failures")
          resp-data (json/parse-string (:body response))]
      (is (= {"failingBuild" {"failedCount" 1 "children" [{"name" "a suite"
                                                           "children" [{"name" "a test"
                                                                        "failedCount" 1}]}]}
              "anotherFailingBuild" {"failedCount" 1 "children" [{"name" "another suite"
                                                                  "children" [{"name" "another test"
                                                                               "failedCount" 1}]}]}}
             resp-data)))
    ))

(deftest TestsuitesSummary
  (testing "GET to /testsuites"
    ;; GET should return 200
    (let [response (json-get-request app "/testsuites")]
      (is (= 200 (:status response))))

    ;; GET should return empty map by default
    (let [response (json-get-request app "/testsuites")
          resp-data (json/parse-string (:body response))]
      (is (= {} resp-data)))

    ;; GET should include a list of builds with test cases
    (reset-app!)
    (a-build "aBuild" 1, {})
    (some-test-results "aBuild" "1" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\" time=\"10\"></testcase></testsuite></testsuites>")
    (a-build "aBuild" 2, {})
    (some-test-results "aBuild" "2" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a test\" time=\"30\"></testcase></testsuite></testsuites>")
    (let [response (json-get-request app "/testsuites")
          resp-data (json/parse-string (:body response))]
      (is (= {"aBuild" {"children" [{"name" "a suite"
                                     "children" [{"name" "a test"
                                                  "averageRuntime" 20000}]}]}}
             resp-data)))

    ;; GET should return CSV by default
    (reset-app!)
    (a-build "aBuild" 1, {})
    (some-test-results "aBuild" "1" "<testsuites><testsuite name=\"a suite\"><testcase name=\"a,test\" classname=\"a class\" time=\"10\"></testcase></testsuite></testsuites>")
    (let [response (get-request app "/testsuites")]
      (is (= (:body response)
             (join ["averageRuntime,job,testsuite,classname,name\n"
                    (format "%.8f,aBuild,a suite,a class,\"a,test\"\n" 0.00011574)]))))

    ;; GET should handle nested testsuites in CSV
    (reset-app!)
    (a-build "aBuild" 1, {})
    (some-test-results "aBuild" "1" "<testsuites><testsuite name=\"a suite\"><testsuite name=\"nested suite\"><testcase name=\"a,test\" classname=\"a class\" time=\"10\"></testcase></testsuite></testsuite></testsuites>")
    (let [response (plain-get-request app "/testsuites")]
      (is (= (:body response)
             (join ["averageRuntime,job,testsuite,classname,name\n"
                    (format "%.8f,aBuild,a suite: nested suite,a class,\"a,test\"\n" 0.00011574)]))))

    ;; GET should not include builds without test cases
    (reset-app!)
    (a-build "aBuild" 1, {})
    (let [response (json-get-request app "/testsuites")
          resp-data (json/parse-string (:body response))]
      (is (= {} resp-data)))
    ))

(deftest EntryPoint
  (testing "GET to /"
    (let [response (get-request app "/")]
      (is (= 302 (:status response))))

    (let [response (get-request app "/")]
      (is (= "/index.html" (get (:headers response) "Location"))))))
