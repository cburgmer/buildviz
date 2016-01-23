(ns buildviz.controllers.wait-times-test
  (:require [buildviz.controllers.wait-times :as sut]
            [buildviz.test-utils
             :refer
             [json-body json-get-request plain-get-request the-app]]
            [clojure.test :refer :all]
            [clojure.string :as str]))

(def a-day (* 24 60 60 1000))

(deftest test-get-wait-times
  (testing "should return wait times"
    (let [app (the-app {"test" {2 {:start (+ 2000 a-day)
                                   :end (+ 3000 a-day)}
                                1 {:start 100
                                   :end 200}}
                        "deploy" {2 {:start (+ 3800 a-day)
                                     :end (+ 5000 a-day)
                                     :triggered-by {:job-name "test"
                                                    :build-id 2}}
                                  1 {:start 700
                                     :end 800
                                     :triggered-by {:job-name "test"
                                                    :build-id 1}}}}
                       {})]
      (is (= [{"job" "deploy"
               "waitTimes" [{"date" "1970-01-01"
                             "waitTime" 500}
                            {"date" "1970-01-02"
                             "waitTime" 800}]}]
             (json-body (json-get-request app "/waittimes"))))))

  (testing "should respect time offset"
    (let [app (the-app {"test" {2 {:start (+ 2000 a-day)
                                   :end (+ 3000 a-day)}
                                1 {:start 100
                                   :end 200}}
                        "deploy" {2 {:start (+ 3800 a-day)
                                     :end (+ 5000 a-day)
                                     :triggered-by {:job-name "test"
                                                    :build-id 2}}
                                  1 {:start 700
                                     :end 800
                                     :triggered-by {:job-name "test"
                                                    :build-id 1}}}}
                       {})]
      (is (= [{"job" "deploy"
               "waitTimes" [{"date" "1970-01-02"
                             "waitTime" 800}]}]
             (json-body (json-get-request app "/waittimes" {"from" a-day}))))))

  (testing "should respond with CSV"
    (let [app (the-app {"test" {2 {:start (+ 2000 a-day)
                                   :end (+ 3000 a-day)}
                                1 {:start 100
                                   :end 200}}
                        "deploy-staging" {1 {:start 700
                                             :end 800
                                             :triggered-by {:job-name "test"
                                                            :build-id 1}}}
                        "deploy-uat" {2 {:start (+ 3800 a-day)
                                         :end (+ 5000 a-day)
                                         :triggered-by {:job-name "test"
                                                        :build-id 2}}}}
                       {})]
      (is (= (str/join "\n" ["date,deploy-staging,deploy-uat"
                             (format "1970-01-01,%.8f," (float (/ 500 a-day)))
                             (format "1970-01-02,,%.8f" (float (/ 800 a-day)))
                             ""])
             (:body (plain-get-request app "/waittimes")))))))
