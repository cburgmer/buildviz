(ns buildviz.controllers.wait-times-test
  (:require [buildviz.controllers.wait-times :as sut]
            [buildviz.test-utils
             :refer
             [json-body json-get-request plain-get-request the-app]]
            [clj-time
             [coerce :as tc]
             [core :as t]]
            [clojure.test :refer :all]
            [clojure.string :as str]))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))
(def a-day (* 24 60 60 1000))

(deftest test-get-wait-times
  (testing "should return wait times"
    (let [app (the-app {"test" {"2" {:start (+ 2000 a-day)
                                     :end (+ 3000 a-day)}
                                "1" {:start 100
                                     :end 200}}
                        "deploy" {"2" {:start (+ 3800 a-day)
                                       :end (+ 5000 a-day)
                                       :triggered-by [{:job-name "test"
                                                       :build-id "2"}]}
                                  "1" {:start 700
                                       :end 800
                                       :triggered-by [{:job-name "test"
                                                       :build-id "1"}]}}}
                       {})]
      (is (= [{"job" "deploy"
               "buildId" "2"
               "start" 86403800
               "waitTime" 800
               "triggeredBy" {"job" "test" "buildId" "2"}}
              {"job" "deploy"
               "buildId" "1"
               "start" 700
               "waitTime" 500
               "triggeredBy" {"job" "test" "buildId" "1"}}]
             (json-body (json-get-request app "/waittimes"))))))

  (testing "should respect time offset"
    (let [app (the-app {"test" {"2" {:start (+ 2000 a-day)
                                     :end (+ 3000 a-day)}
                                "1" {:start 100
                                     :end 200}}
                        "deploy" {"2" {:start (+ 3800 a-day)
                                       :end (+ 5000 a-day)
                                       :triggered-by [{:job-name "test"
                                                       :build-id "2"}]}
                                  "1" {:start 700
                                       :end 800
                                       :triggered-by [{:job-name "test"
                                                       :build-id "1"}]}}}
                       {})]
      (is (= [{"job" "deploy"
               "buildId" "2"
               "start" 86403800
               "waitTime" 800
               "triggeredBy" {"job" "test" "buildId" "2"}}]
             (json-body (json-get-request app "/waittimes" {"from" a-day}))))))

  (testing "should respond with CSV"
    (let [app (the-app {"test" {"1" {:start (+ 100 a-timestamp)
                                     :end (+ 200 a-timestamp)}}
                        "deploy-staging" {"1" {:start (+ 400 a-timestamp)
                                               :end (+ 800 a-timestamp)
                                               :triggered-by [{:job-name "test"
                                                               :build-id "1"}]}}
                        "deploy-uat" {"2" {:start (+ 3800 a-timestamp)
                                           :end (+ 5000 a-timestamp)
                                           :triggered-by [{:job-name "test"
                                                           :build-id "1"}]}}
                        "smoke-test" {"1" {:start (+ 1000 a-timestamp)
                                           :end (+ 1100 a-timestamp)
                                           :triggered-by [{:job-name "test"
                                                           :build-id "1"}]}}}
                       {})]
      (is (= (str/join "\n" ["job,buildId,start,waitTime,triggeredBy"
                             (format "deploy-staging,1,1986-10-14 04:03:27,%.8f,test/1" (float (/ 200 a-day)))
                             (format "smoke-test,1,1986-10-14 04:03:28,%.8f,test/1" (float (/ 800 a-day)))
                             (format "deploy-uat,2,1986-10-14 04:03:31,%.8f,test/1" (float (/ 3600 a-day)))
                             ""])
             (:body (plain-get-request app "/waittimes")))))))
