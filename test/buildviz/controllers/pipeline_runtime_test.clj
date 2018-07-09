(ns buildviz.controllers.pipeline-runtime-test
  (:require [buildviz.controllers.pipeline-runtime :as sut]
            [buildviz.test-utils
             :refer
             [json-body json-get-request plain-get-request the-app]]
            [clj-time
             [coerce :as tc]
             [core :as t]]
            [clojure
             [string :as str]
             [test :refer :all]]))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))
(def a-day (* 24 60 60 1000))

(deftest test-get-pipeline-runtime
  (testing "should return pipeline runtime"
    (let [app (the-app {"test" {"2" {:start (+ 2000 a-day)}
                                "1" {:start 100}}
                        "deploy" {"2" {:start (+ 2500 a-day)
                                       :end (+ 2800 a-day)
                                       :triggered-by [{:job-name "test"
                                                       :build-id "2"}]}
                                  "1" {:start 200
                                       :end 700
                                       :triggered-by [{:job-name "test"
                                                       :build-id "1"}]}}}
                       {})]
      (is (= [{"pipeline" ["test" "deploy"]
               "builds" [{"job" "test"
                          "buildId" "2"}
                         {"job" "deploy"
                          "buildId" "2"}]
               "start" 86402000
               "end" 86402800}
              {"pipeline" ["test" "deploy"]
               "builds" [{"job" "test"
                          "buildId" "1"}
                         {"job" "deploy"
                          "buildId" "1"}]
               "start" 100
               "end" 700}]
             (json-body (json-get-request app "/pipelineruntime"))))))

  (testing "should respect time offset"
    (let [app (the-app {"test" {"1" {:start 100}
                                "2" {:start (+ 2000 a-day)}}
                        "deploy" {"1" {:start 200
                                       :end 700
                                       :triggered-by [{:job-name "test"
                                                       :build-id "1"}]}
                                  "2" {:start (+ 2500 a-day)
                                       :end (+ 2800 a-day)
                                       :triggered-by [{:job-name "test"
                                                       :build-id "2"}]}}}
                       {})]
      (is (= [{"pipeline" ["test" "deploy"]
               "builds" [{"job" "test"
                          "buildId" "2"}
                         {"job" "deploy"
                          "buildId" "2"}]
               "start" 86402000
               "end" 86402800}]
             (json-body (json-get-request app "/pipelineruntime" {"from" a-day}))))))

  (testing "should respond with CSV"
    (let [app (the-app {"test" {"1" {:start (+ 100 a-timestamp)}
                                "2" {:start (+ 2000 a-day a-timestamp)}}
                        "deploy-staging" {"1" {:start (+ 200 a-timestamp)
                                               :end (+ 700 a-timestamp)
                                               :triggered-by [{:job-name "test"
                                                               :build-id "1"}]}}
                        "deploy-uat" {"2" {:start (+ 2500 a-day a-timestamp)
                                           :end (+ 2800 a-day a-timestamp)
                                           :triggered-by [{:job-name "test"
                                                           :build-id "2"}]}}}
                       {})]
      (is (= (str/join "\n" ["pipeline,start,end,builds"
                             "test|deploy-staging,1986-10-14 04:03:27,1986-10-14 04:03:28,test/1|deploy-staging/1"
                             "test|deploy-uat,1986-10-15 04:03:29,1986-10-15 04:03:30,test/2|deploy-uat/2"
                             ""])
             (:body (plain-get-request app "/pipelineruntime")))))))
