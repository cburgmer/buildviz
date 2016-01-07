(ns buildviz.controllers.pipeline-runtime-test
  (:require [buildviz.controllers.pipeline-runtime :as sut]
            [buildviz.test-utils
             :refer
             [json-body json-get-request plain-get-request the-app]]
            [clojure
             [string :as str]
             [test :refer :all]]))

(def a-day (* 24 60 60 1000))

(deftest test-get-pipeline-runtime
  (testing "should return pipeline runtime"
    (let [app (the-app {"test" {1 {:start 100}
                                2 {:start 2000}}
                        "deploy" {1 {:start 200
                                     :end 700
                                     :triggered-by {:job-name "test"
                                                    :build-id 1}}
                                  2 {:start 2500
                                     :end 2800
                                     :triggered-by {:job-name "test"
                                                    :build-id 2}}}}
                       {})]
      (is (= [{"pipeline" ["test" "deploy"]
               "runtimes" [{"date" "1970-01-01"
                            "runtime" 700}]}]
             (json-body (json-get-request app "/pipelineruntime"))))))

  (testing "should respect time offset"
    (let [app (the-app {"test" {1 {:start 100}
                                2 {:start (+ 2000 a-day)}}
                        "deploy" {1 {:start 200
                                     :end 700
                                     :triggered-by {:job-name "test"
                                                    :build-id 1}}
                                  2 {:start (+ 2500 a-day)
                                     :end (+ 2800 a-day)
                                     :triggered-by {:job-name "test"
                                                    :build-id 2}}}}
                       {})]
      (is (= [{"pipeline" ["test" "deploy"]
               "runtimes" [{"date" "1970-01-02"
                            "runtime" 800}]}]
             (json-body (json-get-request app "/pipelineruntime" {"from" a-day}))))))

  (testing "should respond with CSV"
    (let [app (the-app {"test" {1 {:start 100}
                                2 {:start (+ 2000 a-day)}}
                        "deploy-staging" {1 {:start 200
                                             :end 700
                                             :triggered-by {:job-name "test"
                                                            :build-id 1}}}
                        "deploy-uat" {2 {:start (+ 2500 a-day)
                                         :end (+ 2800 a-day)
                                         :triggered-by {:job-name "test"
                                                        :build-id 2}}}}
                       {})]
      (is (= (str/join "\n" ["date,test|deploy-staging,test|deploy-uat"
                             (format "1970-01-01,%.8f," (float (/ 600 a-day)))
                             (format "1970-01-02,,%.8f" (float (/ 800 a-day)))
                             ""])
             (:body (plain-get-request app "/pipelineruntime")))))))
