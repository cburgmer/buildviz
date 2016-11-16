(ns buildviz.analyse.fail-phases-test
  (:require [buildviz.analyse.fail-phases :refer :all]
            [clojure.test :refer :all]))

(deftest PipelineInfo
  (testing "pipeline-phases"
    (is (= []
           (pipeline-phases [])))
    (is (= []
           (pipeline-phases [{:end 20 :outcome "pass" :job "a job"}])))
    (is (= []
           (pipeline-phases [{:end 20 :outcome "fail" :job "a job"}])))
    (is (= [{:start 20 :end 40 :status "pass"}]
           (pipeline-phases [{:end 20 :outcome "pass" :job "a job"}
                             {:end 40 :outcome "pass" :job "a job"}])))
    (is (= [{:start 20 :end 40 :status "fail" :culprits #{"a job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 20 :outcome "fail" :job "a job"}
                             {:end 40 :outcome "pass" :job "a job"}])))
    (is (= [{:start 20 :end 40 :status "fail" :culprits #{"a job"} :ongoing-culprits #{"a job"}}]
           (pipeline-phases [{:end 20 :outcome "fail" :job "a job"}
                             {:end 40 :outcome "fail" :job "a job"}])))
    (is (= [{:start 20 :end 40 :status "pass"}
            {:start 40 :end 60 :status "fail" :culprits #{"a job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 20 :outcome "pass" :job "a job"}
                             {:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "pass" :job "a job"}])))
    (is (= [{:start 20 :end 40 :status "pass"}
            {:start 40 :end 60 :status "fail" :culprits #{"a job"} :ongoing-culprits #{}}
            {:start 60 :end 80 :status "pass"}
            {:start 80 :end 100 :status "fail" :culprits #{"a job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 20 :outcome "pass" :job "a job"}
                             {:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "pass" :job "a job"}
                             {:end 80 :outcome "fail" :job "a job"}
                             {:end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 80 :status "fail" :culprits #{"a job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "fail" :job "a job"}
                             {:end 80 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 80 :status "fail" :culprits #{"a job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "pass" :job "another job"}
                             {:end 80 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 100 :status "fail" :culprits #{"a job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "pass" :job "another job"}
                             {:end 80 :outcome "fail" :job "a job"}
                             {:end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 100 :status "fail" :culprits #{"a job", "another job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "fail" :job "another job"}
                             {:end 80 :outcome "pass" :job "a job"}
                             {:end 100 :outcome "pass" :job "another job"}])))
    (is (= [{:start 40 :end 100 :status "fail" :culprits #{"a job", "another job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "fail" :job "another job"}
                             {:end 80 :outcome "pass" :job "another job"}
                             {:end 100 :outcome "pass" :job "a job"}])))
    (is (= [{:start 40 :end 80 :status "fail" :culprits #{"a job", "another job"} :ongoing-culprits #{"a job"}}]
           (pipeline-phases [{:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "fail" :job "another job"}
                             {:end 80 :outcome "pass" :job "another job"}])))
    (is (= [{:start 20 :end 40 :status "pass"}
            {:start 40 :end 140 :status "fail" :culprits #{"a job", "another job", "yet another job"} :ongoing-culprits #{}}]
           (pipeline-phases [{:end 20 :outcome "pass" :job "a job"}
                             {:end 40 :outcome "fail" :job "a job"}
                             {:end 60 :outcome "fail" :job "another job"}
                             {:end 80 :outcome "pass" :job "another job"}
                             {:end 100 :outcome "fail" :job "yet another job"}
                             {:end 120 :outcome "pass" :job "yet another job"}
                             {:end 140 :outcome "pass" :job "a job"}]))))

  (testing "pipeline-fail-phases"
    (is (= [{:start 40 :end 140 :culprits #{"a job", "another job", "yet another job"}}]
           (pipeline-fail-phases [{:end 20 :outcome "pass" :job "a job"}
                                  {:end 40 :outcome "fail" :job "a job"}
                                  {:end 60 :outcome "fail" :job "another job"}
                                  {:end 80 :outcome "pass" :job "another job"}
                                  {:end 100 :outcome "fail" :job "yet another job"}
                                  {:end 120 :outcome "pass" :job "yet another job"}
                                  {:end 140 :outcome "pass" :job "a job"}])))))
