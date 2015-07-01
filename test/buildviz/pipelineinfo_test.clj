(ns buildviz.pipelineinfo-test
  (:use clojure.test
        buildviz.pipelineinfo))


(deftest PipelineInfo
  (testing "pipeline-fail-phases"
    (is (= []
           (pipeline-fail-phases [])))
    (is (= []
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}])))
    (is (= [{:start 10 :end 30 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "fail" :job "a job"}
                                  {:start 30 :end 40 :outcome "pass" :job "a job"}])))
    (is (= [{:start 30 :end 50 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}
                                  {:start 30 :end 40 :outcome "fail" :job "a job"}
                                  {:start 50 :end 60 :outcome "pass" :job "a job"}])))
    (is (= [{:start 30 :end 50 :culprits #{"a job"}} {:start 70 :end 90 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}
                                  {:start 30 :end 40 :outcome "fail" :job "a job"}
                                  {:start 50 :end 60 :outcome "pass" :job "a job"}
                                  {:start 70 :end 80 :outcome "fail" :job "a job"}
                                  {:start 90 :end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 30 :end 70 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}
                                  {:start 30 :end 40 :outcome "fail" :job "a job"}
                                  {:start 50 :end 60 :outcome "fail" :job "a job"}
                                  {:start 70 :end 80 :outcome "pass" :job "a job"}])))
    (is (= [{:start 30 :end 70 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}
                                  {:start 30 :end 40 :outcome "fail" :job "a job"}
                                  {:start 50 :end 60 :outcome "pass" :job "another job"}
                                  {:start 70 :end 80 :outcome "pass" :job "a job"}])))
    (is (= [{:start 30 :end 90 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}
                                  {:start 30 :end 40 :outcome "fail" :job "a job"}
                                  {:start 50 :end 60 :outcome "pass" :job "another job"}
                                  {:start 70 :end 80 :outcome "fail" :job "a job"}
                                  {:start 90 :end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 30 :end 90 :culprits #{"a job", "another job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}
                                  {:start 30 :end 40 :outcome "fail" :job "a job"}
                                  {:start 50 :end 60 :outcome "fail" :job "another job"}
                                  {:start 70 :end 80 :outcome "pass" :job "a job"}
                                  {:start 90 :end 100 :outcome "pass" :job "another job"}])))
    (is (= [{:start 30 :end 90 :culprits #{"a job", "another job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}
                                  {:start 30 :end 40 :outcome "fail" :job "a job"}
                                  {:start 50 :end 60 :outcome "fail" :job "another job"}
                                  {:start 70 :end 80 :outcome "pass" :job "another job"}
                                  {:start 90 :end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 30 :end 130 :culprits #{"a job", "another job", "yet another job"}}]
           (pipeline-fail-phases [{:start 10 :end 20 :outcome "pass" :job "a job"}
                                  {:start 30 :end 40 :outcome "fail" :job "a job"}
                                  {:start 50 :end 60 :outcome "fail" :job "another job"}
                                  {:start 70 :end 80 :outcome "pass" :job "another job"}
                                  {:start 90 :end 100 :outcome "fail" :job "yet another job"}
                                  {:start 110 :end 120 :outcome "pass" :job "yet another job"}
                                  {:start 130 :end 140 :outcome "pass" :job "a job"}])))))
