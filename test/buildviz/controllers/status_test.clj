(ns buildviz.controllers.status-test
  (:require [buildviz
             [handler :as handler]
             [test-utils :refer :all]]
            [buildviz.data.results :as results]
            [clj-time
             [coerce :as tc]
             [core :as t]]
            [clojure.test :refer :all]))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))
(def a-day (* 24 60 60 1000))

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
    (let [pipeline-name "Test Pipeline"
          app (handler/create-app (results/build-results {}
                                                         (fn [])
                                                         dummy-persist
                                                         dummy-persist)
                                  pipeline-name)
          body (json-body (json-get-request (the-app) "/status"))]
      (is (= pipeline-name
             (get body "pipelineName"))))))
