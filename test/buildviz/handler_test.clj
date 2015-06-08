(ns buildviz.handler-test
  (:use clojure.test
        ring.mock.request
        buildviz.handler)
  (:require [cheshire.core :as json]))

(defn each-fixture [f]
  (reset! builds {})
  (f))

(use-fixtures :each each-fixture)

(defn a-build [jobName, buildNr, content]
  (app (-> (request :put
                    (format "/builds/%s/%s" jobName buildNr))
           (body (json/generate-string content))
           (content-type "application/json"))))

(deftest Storage
  (testing "PUT to /builds/:job/:build"
    ;; PUT should return 200
    (let [response (app (request :put "/builds/mybuild/1"))]
      (is (= (:status response) 200)))

    ; PUT should return content
    (let [response (app (-> (request :put
                                     "/builds/abuild/1")
                            (body (json/generate-string {:start 42 :end 43}))
                            (content-type "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43}))))

  (testing "GET to /builds/:job/:build"
    ;; GET should return 200
    (a-build "anotherBuild" 1 {:start 42 :end 43})
    (let [response (app (request :get "/builds/anotherBuild/1"))]
      (is (= (:status response) 200)))

    ;; GET should return content stored by PUT
    (a-build "yetAnotherBuild" 1, {:start 42 :end 43})
    (let [response (app (request :get "/builds/yetAnotherBuild/1"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43})))

    ;; GET should return 404 if job not found
    (let [response (app (request :get "/builds/unknownBuild/10"))]
      (is (= (:status response) 404)))

    ;; GET should return 404 if build not found
    (a-build "anExistingBuild" 1, {:start 42 :end 43})
    (let [response (app (request :get "/builds/anExistingBuild/2"))]
      (is (= (:status response) 404)))

    ;; Different jobs should not interfere with each other
    (a-build "someBuild" 1, {:start 42 :end 43})
    (let [response (app (request :get "/builds/totallyUnrelatedBuild/1"))
          resp-data (json/parse-string (:body response))]
      (is (= (:status response) 404)))))

(deftest PipelineSummary
  (testing "GET to /pipeline"
    ; GET should return 200
    (let [response (app (request :get "/pipeline"))]
      (is (= (:status response) 200)))

    ;; GET should return empty map by default
    (let [response (app (request :get "/pipeline"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {})))

    ;; GET should return job summary
    (a-build "someBuild" 1, {:start 42 :end 43})
    (a-build "anotherBuild" 1, {:start 10 :end 12})
    (let [response (app (request :get "/pipeline"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"someBuild" {"averageRuntime" 1 "totalCount" 1}
                        "anotherBuild" {"averageRuntime" 2 "totalCount" 1}})))

    ;; GET should return total build count
    (reset! builds {})
    (a-build "runOnce" 1, {})
    (a-build "runTwice" 1, {})
    (a-build "runTwice" 2, {})
    (let [response (app (request :get "/pipeline"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"runTwice" {"totalCount" 2}
                        "runOnce" {"totalCount" 1}})))

    ;; GET should return failed build count
    (reset! builds {})
    (a-build "flakyBuild" 1, {:outcome "pass"})
    (a-build "flakyBuild" 2, {:outcome "fail"})
    (a-build "brokenBuild" 1, {:outcome "fail"})
    (let [response (app (request :get "/pipeline"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"flakyBuild" {"failedCount" 1 "totalCount" 2 "flakyCount" 0}
                        "brokenBuild" {"failedCount" 1 "totalCount" 1 "flakyCount" 0}})))

    ;; GET should return error build count
    (reset! builds {})
    (a-build "goodBuild" 1, {:outcome "pass"})
    (let [response (app (request :get "/pipeline"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"goodBuild" {"failedCount" 0 "totalCount" 1 "flakyCount" 0}})))

    ;; GET should return a flaky build count
    (reset! builds {})
    (a-build "flakyBuild" 1, {:outcome "pass" :inputs {:id 42 :revision "dat_revision"}})
    (a-build "flakyBuild" 2, {:outcome "fail" :inputs {:id 42 :revision "dat_revision"}})
    (let [response (app (request :get "/pipeline"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data {"flakyBuild" {"failedCount" 1 "totalCount" 2 "flakyCount" 1}})))
    ))
