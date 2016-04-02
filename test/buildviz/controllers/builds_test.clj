(ns buildviz.controllers.builds-test
  (:require [buildviz
             [handler :as handler]
             [test-utils :refer :all]]
            [buildviz.data.results :as results]
            [clojure.test :refer :all]))

(defn some-test-results [app job-name build-no content]
  (xml-put-request app
                   (format "/builds/%s/%s/testresults" job-name build-no)
                   content))


(defn- the-app-with-builds [builds]
  (handler/create-app (results/build-results @builds
                                             (fn [_ _])
                                             (fn [job-name build-id build]
                                               (swap! builds assoc-in [job-name build-id] build))
                                             (fn [_ _ _]))
                      "Test Pipeline"))

(deftest test-store-build!
  (testing "PUT to /builds/:job/:build"
    ;; PUT should return 200
    (is (= 200
           (:status (json-put-request (the-app) "/builds/abuild/1" {:start 1453646247759}))))

    ;; PUT should return 400 for unknown parameters
    (is (= 400
           (:status (json-put-request (the-app) "/builds/abuild/1" {:unknown "value"}))))

    ;; PUT should return 400 for illegal outcome
    (is (= 400
           (:status (json-put-request (the-app) "/builds/abuild/1" {:outcome "banana"}))))

    ;; PUT should return content
    (let [response (json-put-request (the-app) "/builds/abuild/1" {:start 1453646247759 :end 1453646247760})
          resp-data (json-body response)]
      (is (= {"start" 1453646247759 "end" 1453646247760}
             resp-data))))

  (testing "should store build"
    (let [builds (atom {})
          app (the-app-with-builds builds)]
      (json-put-request app "/builds/abuild/1" {:start 1453646247759})
      (is (= {:start 1453646247759}
             (get-in @builds ["abuild" "1"])))))

  (testing "should convert from camel-case"
    (let [builds (atom {})
          app (the-app-with-builds builds)]
      (json-put-request app "/builds/abuild/1" {:start 1453646247759 :inputs [{:revision "xyz" :sourceId 21}]})
      (is (= {:revision "xyz" :source-id 21}
             (first (:inputs (get-in @builds ["abuild" "1"]))))))))

(deftest test-get-build
  (testing "GET to /builds/:job/:build"
    ;; GET should return 200
    (let [app (the-app {"anotherBuild" {"1" {:start 42 :end 43}}} {})]
      (is (= 200
             (:status (get-request app "/builds/anotherBuild/1")))))

    ;; GET should return content stored by PUT
    (let [app (the-app {"yetAnotherBuild" {"1" {:start 42 :end 43}}} {})]
      (is (= {"start" 42 "end" 43}
             (json-body (get-request app "/builds/yetAnotherBuild/1")))))

    ;; GET should return 404 if job not found
    (is (= 404
           (:status (get-request (the-app) "/builds/unknownBuild/10"))))

    ;; GET should return 404 if build not found
    (let [app (the-app {"anExistingBuild" {1 {:start 42 :end 43}}} {})]
      (is (= 404
             (:status (get-request app "/builds/anExistingBuild/2")))))

    ;; Different jobs should not interfere with each other
    (let [app (the-app {"someBuild" {1 {:start 42 :end 43}}} {})]
      (is (= 404
             (:status (get-request app "/builds/totallyUnrelatedBuild/1")))))))


(deftest test-store-test-results!
  (testing "PUT with XML"
    (is (= 204 (:status (xml-put-request (the-app) "/builds/mybuild/1/testresults" "<testsuites></testsuites>"))))
    (is (= 400 (:status (xml-put-request (the-app) "/builds/mybuild/1/testresults" "not xml"))))
    (is (= 400 (:status (xml-put-request (the-app) "/builds/mybuild/1/testresults" "<testsuite name=\"suite\"><testcase classname=\"class\"/></testsuite>")))))

  (testing "PUT with JSON"
    (is (= 204 (:status (json-put-request (the-app) "/builds/somebuild/42/testresults" [{:name "Some Testsuite"
                                                                                         :children [{:name "A Test"
                                                                                                     :classname "The Class"
                                                                                                     :runtime 21
                                                                                                     :status "pass"}]}]))))
    (testing "should store as JUnit XML"
      (let [test-results (atom {})
            app (the-app {} test-results)]
        (json-put-request app "/builds/somebuild/42/testresults" [{:name "Some Testsuite"
                                                                   :children [{:name "A Test"
                                                                               :classname "A Class"
                                                                               :runtime 21
                                                                               :status "fail"}]}])
        (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"Some Testsuite\"><testcase name=\"A Test\" time=\"0.021\" classname=\"A Class\"><failure></failure></testcase></testsuite></testsuites>"
               (get-in @test-results ["somebuild" "42"])))))

    (testing "should fail on invalid testsuite"
      (is (= 400 (:status (json-put-request (the-app) "/builds/somebuild/42/testresults" [{:name "A Suite without children"}]))))))

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
