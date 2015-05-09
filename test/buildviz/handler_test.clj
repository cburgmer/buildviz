(ns buildviz.handler-test
  (:use clojure.test
        ring.mock.request
        buildviz.handler)
  (:require [cheshire.core :as json]))

(deftest test-app
  (testing "app"
    ; PUT should return 200
    (let [response (app (request :put "/builds/mybuild/1"))]
      (is (= (:status response) 200)))

    ; PUT should return content
    (let [response (app (-> (request :put
                                     "/builds/abuild/1")
                            (body (json/generate-string {:start 42 :end 43}))
                            (content-type "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43})))

    ; GET should return 200
    (app (-> (request :put
                      "/builds/anotherBuild/1")
             (body (json/generate-string {:start 42 :end 43}))
             (content-type "application/json")))
    (let [response (app (request :get "/builds/anotherBuild/1"))]
      (is (= (:status response) 200)))

    ; GET should return content stored by PUT
    (app (-> (request :put
                  "/builds/yetAnotherBuild/1")
             (body (json/generate-string {:start 42 :end 43}))
             (content-type "application/json")))
    (let [response (app (request :get "/builds/yetAnotherBuild/1"))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43})))

    ; GET should return 404 if not found
    (let [response (app (request :get "/builds/unknownBuild/10"))]
      (is (= (:status response) 404)))))
