(ns buildviz.controllers.pipeline-runtime-test
  (:require [buildviz.controllers.pipeline-runtime :as sut]
            [buildviz.test-utils :refer [json-body json-get-request the-app]]
            [clojure.test :refer :all]))

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
             (json-body (json-get-request app "/pipelineruntime")))))))
