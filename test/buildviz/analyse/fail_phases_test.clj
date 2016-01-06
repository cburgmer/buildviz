(ns buildviz.analyse.fail-phases-test
  (:require [buildviz.analyse.fail-phases :refer :all]
            [clojure.test :refer :all]))

(deftest PipelineInfo
  (testing "pipeline-fail-phases"
    (is (= []
           (pipeline-fail-phases [])))
    (is (= []
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}])))
    (is (= [{:start 20 :end 40 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "fail" :job "a job"}
                                  {:end 40 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 60 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 60 :culprits #{"a job"}} {:start 80 :end 100 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "pass" :job "a job"}
                                  {:end 80 :outcome "fail" :job "a job"}
                                  {:end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 80 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "fail" :job "a job"}
                                  {:end 80 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 80 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "pass" :job "another job"}
                                  {:end 80 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 100 :culprits #{"a job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "pass" :job "another job"}
                                  {:end 80 :outcome "fail" :job "a job"}
                                  {:end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 100 :culprits #{"a job", "another job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "fail" :job "another job"}
                                  {:end 80 :outcome "pass" :job "a job"}
                                  {:end 100 :outcome "pass" :job "another job"}])))
    (is (= [{:start 40 :end 100 :culprits #{"a job", "another job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "fail" :job "another job"}
                                  {:end 80 :outcome "pass" :job "another job"}
                                  {:end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 140 :culprits #{"a job", "another job", "yet another job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "fail" :job "another job"}
                                  {:end 80 :outcome "pass" :job "another job"}
                                  {:end 100 :outcome "fail" :job "yet another job"}
                                  {:end 120 :outcome "pass" :job "yet another job"}
                                  {:end 140 :outcome "pass" :job "a job"}])))))
