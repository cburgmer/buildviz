(ns buildviz.handler-test
  (:use clojure.test
        ring.mock.request
        buildviz.handler)
  (:require [cheshire.core :as json]))

(deftest test-app
  (testing "app"
    (let [response (app (request :put "/builds/mybuild/1"))]
      (is (= (:status response) 200)))

    (let [response (app (-> (request :put
                                     "/builds/mybuild/1")
                            (body (json/generate-string {:start 42 :end 43}))
                            (content-type "application/json")))
          resp-data (json/parse-string (:body response))]
      (is (= resp-data
             {"start" 42 "end" 43})))))
