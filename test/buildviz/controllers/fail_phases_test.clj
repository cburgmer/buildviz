(ns buildviz.controllers.fail-phases-test
  (:require [buildviz.test-utils :refer :all]
            [clj-time
             [coerce :as tc]
             [core :as t]]
            [clojure.test :refer :all]))

(def a-timestamp (tc/to-long (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone))))

(deftest FailPhases
  (testing "GET to /failphases"
    ;; GET should return 200
    (is (= 200
           (:status (get-request (the-app) "/failphases"))))

    ;; GET should return empty list by default
    (is (= "start,end,status,culprits,ongoing_culprits\n"
           (:body (get-request (the-app) "/failphases"))))

    ;; GET should return fail phases
    (let [app (the-app {"badBuild" {1 {:start 0 :end a-timestamp :outcome "fail"}
                                    2 {:start 0 :end (+ a-timestamp 30000) :outcome "pass"}}
                        "anotherBuild" {1 {:start 0 :end (+ a-timestamp 10000) :outcome "fail"}
                                        2 {:start 0 :end (+ a-timestamp 20000) :outcome "pass"}}}
                       {})]
      (is (= "start,end,status,culprits,ongoing_culprits\n1986-10-14 04:03:27,1986-10-14 04:03:57,fail,anotherBuild|badBuild,\n"
             (:body (get-request app "/failphases")))))

    ;; GET should return empty list by default as JSON
    (is (= []
           (json-body (json-get-request (the-app) "/failphases"))))

    ;; GET should return fail phases as JSON
    (let [app (the-app {"badBuild" {1 {:start 0 :end 42 :outcome "fail"}
                                    2 {:start 70 :end 80 :outcome "pass"}}}
                       {})]
      (is (= [{"start" 42 "end" 80 "culprits" ["badBuild"]}]
             (json-body (json-get-request app "/failphases"))))))

  (testing "should respect 'from' filter"
    (let [app (the-app {"badBuild" {1 {:start 20 :end 42 :outcome "fail"}
                                    2 {:start 70 :end 80 :outcome "pass"}
                                    3 {:start 90 :end 100 :outcome "fail"}
                                    4 {:start 190 :end 200 :outcome "pass"}}}
                       {})]
      (is (= [{"start" 100 "end" 200 "culprits" ["badBuild"]}]
           (json-body (json-get-request app "/failphases" {"from" 60})))))))
