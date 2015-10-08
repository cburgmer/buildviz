(ns buildviz.build-results-test
  (:use clojure.test)
  (:require [buildviz.build-results :as results]))


(deftest test-build-data-validation-errors
  (testing "should pass on 0 end time"
    (is (empty? (results/build-data-validation-errors {:end 0}))))

  (testing "should fail on negative end time"
    (is (not (empty? (results/build-data-validation-errors {:end -1})))))

  (testing "should fail on end time before start time"
    (is (not (empty? (results/build-data-validation-errors {:start 42
                                                            :end 41}))))))

(defn- dummy-store [_ _ _])
(defn- dummy-load-tests [_ _])

(deftest test-build-results
  (testing "should return tests for a build"
    (let [load-tests (fn [job-name build-id]
                       (get-in {"aJob" {"1" "<thexml>"}}
                               [job-name build-id]))
          build-results (results/build-results {} load-tests dummy-store dummy-store)]
      (is (= "<thexml>"
             (results/tests build-results "aJob" "1")))))

  (testing "should return all tests for existing builds"
    (let [load-tests (fn [job-name build-id]
                       (get-in {"aJob" {"1" "<thexml>"
                                        "2" "<morexml>"}
                                "anotherJob" {"3" "<somemorexml>"}}
                               [job-name build-id]))
          build-results (results/build-results {"aJob" {"1" {}
                                                        "2" {}
                                                        "4" {}}
                                                "anotherJob" {"3" {}}}
                                               load-tests
                                               dummy-store
                                               dummy-store)]
      (is (= '("<thexml>" "<morexml>")
             (results/chronological-tests build-results "aJob" nil)))))

  (testing "should return all tests after a given timestamp"
    (let [load-tests (fn [job-name build-id]
                       (get-in {"aJob" {"1" "<thexml>"
                                        "2" "<morexml>"
                                        "4" "<evenmorexml>"}
                                "anotherJob" {"3" "<somemorexml>"}}
                               [job-name build-id]))
          build-results (results/build-results {"aJob" {"1" {:start 42}
                                                        "2" {:start 100}
                                                        "4" {:start 200}}
                                                "anotherJob" {"3" {:start 150}}}
                                               load-tests
                                               dummy-store
                                               dummy-store)]
      (is (= '("<evenmorexml>" "<morexml>")
             (results/chronological-tests build-results "aJob" 100)))))

  (testing "should persist a build"
    (let [fake-storage (atom {})
          store-build! (fn [job-name build-id build]
                        (swap! fake-storage assoc-in [job-name build-id] build))
          build-results (results/build-results {}
                                               dummy-load-tests
                                               store-build!
                                               dummy-store)]
      (results/set-build! build-results "someJob" "42" {:start 4711})
      (is (= {:start 4711}
             (get-in @fake-storage ["someJob" "42"])))))

  (testing "should persist testresults"
    (let [fake-storage (atom {})
          store-tests! (fn [job-name build-id xml]
                         (swap! fake-storage assoc-in [job-name build-id] xml))
          build-results (results/build-results {}
                                               dummy-load-tests
                                               dummy-store
                                               store-tests!)]
      (results/set-tests! build-results "anotherJob" "21" "<the-xml/>")
      (is (= "<the-xml/>"
             (get-in @fake-storage ["anotherJob" "21"]))))))
