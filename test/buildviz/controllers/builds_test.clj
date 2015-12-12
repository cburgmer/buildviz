(ns buildviz.controllers.builds-test
  (:require [buildviz.test-utils :refer :all]
            [clojure.test :refer :all]))

(defn some-test-results [app job-name build-no content]
  (xml-put-request app
                   (format "/builds/%s/%s/testresults" job-name build-no)
                   content))

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
          resp-data (json-body response)]
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
