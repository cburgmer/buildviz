(ns buildviz.go.sync-jobs-test
  (:require [buildviz.go.sync-jobs :as sut]
            [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.fake :as fake]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [clojure.test :refer :all]))

(defn- successful-json-response [body]
  (fn [_] {:status 200
           :body (j/generate-string body)}))

(defn- successful-response [body]
  (fn [_] {:status 200
           :body body}))

(defn- a-stage [name]
  {:name name})

(defn- a-pipeline [name & stages]
  {:name name
   :stages stages})

(defn- a-pipeline-group [name & pipelines]
  {:name name
   :pipelines pipelines})

(defn- a-config [& pipeline-groups]
  [["http://gocd:8513/api/config/pipeline_groups"
    (successful-json-response pipeline-groups)]])

(defn- a-job-run [name scheduled-date id]
  {:name name
   :scheduled_date scheduled-date
   :id id})

(defn- a-stage-run [pipeline-run stage-run result & jobs]
  {:pipeline_counter pipeline-run
   :counter stage-run
   :result result
   :jobs jobs})

(defn- a-short-history [pipeline-name stage-name & stage-runs]
  [[(format "http://gocd:8513/api/stages/%s/%s/history/0" pipeline-name stage-name)
    (successful-json-response {:stages stage-runs})]
   [(format "http://gocd:8513/api/stages/%s/%s/history/%s" pipeline-name stage-name (count stage-runs))
    (successful-json-response {:stages '()})]])

(defn- a-material-revision [revision id]
  {:modifications [{:revision revision}]
   :material {:id id}})

(defn- a-pipeline-run [pipeline-name pipeline-run & revisions]
  [[(format "http://gocd:8513/api/pipelines/%s/instance/%s" pipeline-name pipeline-run)
    (successful-json-response {:build_cause {:material_revisions revisions}})]])

(defn- cruise-property [name value]
  (xml/element :property {:name name} (xml/->CData value)))

(defn- go-date-format [datetime]
  (tf/unparse (:date-time tf/formatters) datetime))

(defn- build-properties [{:keys [start-time end-time actual-stage-run outcome]}]
  (xml/emit-str (xml/element
                 :job {}
                 (xml/element
                  :properties {}
                  (cruise-property "cruise_job_result" outcome)
                  (cruise-property "cruise_timestamp_04_building" (go-date-format start-time))
                  (cruise-property "cruise_timestamp_06_completed" (go-date-format end-time))
                  (cruise-property "cruise_stage_counter" actual-stage-run)))))

(defn- a-builds-properties [job-id content]
  [[(format "http://gocd:8513/api/jobs/%s.xml" job-id)
    (successful-response (build-properties content))]])

(defn- a-file-list [pipeline-name pipeline-run stage-name stage-run build-name & files]
  [[(format "http://gocd:8513/files/%s/%s/%s/%s/%s.json"
            pipeline-name pipeline-run stage-name stage-run build-name)
    (successful-json-response files)]])

(defn- a-file [pipeline-name pipeline-run stage-name stage-run build-name file-path content]
  [[(format "http://gocd:8513/files/%s/%s/%s/%s/%s/%s"
            pipeline-name pipeline-run stage-name stage-run build-name file-path)
    (successful-response content)]])

(defn- provide-buildviz-and-capture-puts [map-ref]
  [[#"http://buildviz:8010/builds/([^/]+)/([^/]+)"
    (fn [req]
      (swap! map-ref #(conj % [(:uri req)
                               (j/parse-string (slurp (:body req)) true)]))
      {:status 200 :body ""})]
   [#"http://buildviz:8010/builds/([^/]+)/([^/]+)/testresults"
    (fn [req]
      (swap! map-ref #(conj % [(:uri req)
                               (slurp (:body req))]))
      {:status 204 :body ""})]])

(defn- serve-up [& routes]
  (->> routes
       (mapcat identity)
       (into {})))

(def beginning-of-2016 (t/date-time 2016 1 1))


(deftest test-sync-jobs
  (testing "should handle no pipeline groups"
    (fake/with-fake-routes-in-isolation (serve-up (a-config))
      (with-out-str (sut/sync-stages (url/url "http://gocd:8513") (url/url "http://buildviz:8010") beginning-of-2016 nil nil))))

  (testing "should handle empty pipeline group"
    (fake/with-fake-routes-in-isolation (serve-up (a-config (a-pipeline-group "Development")))
      (with-out-str (sut/sync-stages (url/url "http://gocd:8513") (url/url "http://buildviz:8010") beginning-of-2016 nil nil))))

  (testing "should handle empty pipeline"
    (fake/with-fake-routes-in-isolation (serve-up (a-config (a-pipeline-group "Development"
                                                                              (a-pipeline "Build"))))
      (with-out-str (sut/sync-stages (url/url "http://gocd:8513") (url/url "http://buildviz:8010") beginning-of-2016 nil nil))))

  (testing "should sync a stage"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "AlphaJob" 1493201298062 321)))
                  (a-pipeline-run "Build" 42
                                  (a-material-revision "AnotherPipeline/21" 7))
                  (a-builds-properties 321
                                       {:start-time (t/date-time 2017 1 1 10 0 0)
                                        :end-time (t/date-time 2017 1 1 12 0)
                                        :outcome "Passed"
                                        :actual-stage-run "1"})
                  (a-file-list "Build" 42 "DoStuff" "1" "AlphaJob")
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (= [["/builds/Build%20%3A%3A%20DoStuff/42%201" {:start 1483264800000
                                                          :end 1483272000000
                                                          :outcome "pass"
                                                          :inputs [{:revision "AnotherPipeline/21", :sourceId 7}]}]]
             @store))))

  (testing "should sync a failing stage"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Failed"
                                                (a-job-run "AlphaJob" 1493201298062 321)))
                  (a-pipeline-run "Build" 42)
                  (a-builds-properties 321
                                       {:start-time (t/date-time 2017 1 1 10 0 0)
                                        :end-time (t/date-time 2017 1 1 12 0)
                                        :outcome "Failed"
                                        :actual-stage-run "1"})
                  (a-file-list "Build" 42 "DoStuff" "1" "AlphaJob")
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (= [["/builds/Build%20%3A%3A%20DoStuff/42%201" {:start 1483264800000
                                                          :end 1483272000000
                                                          :outcome "fail"
                                                          :inputs []}]]
             @store))))

  (testing "should ignore an ongoing stage"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Unknown"
                                                (a-job-run "AlphaJob" 1493201298062 321)))
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (empty? @store))))

  (testing "should ignore a stage who's job ran before the sync date offset"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "AlphaJob"
                                                           (- (tc/to-long beginning-of-2016)
                                                              2)
                                                           321)
                                                (a-job-run "BetaJob"
                                                           (+ (tc/to-long beginning-of-2016)
                                                              9001)
                                                           987)))
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (empty? @store))))

  (testing "should only sync stage of pipeline that's after the sync date offset"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff")
                                                          (a-stage "SomeMore"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "AlphaJob"
                                                           (- (tc/to-long beginning-of-2016)
                                                              2)
                                                           321)))
                  (a-short-history "Build" "SomeMore"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "SomeJob"
                                                           (tc/to-long beginning-of-2016)
                                                           987)))
                  (a-pipeline-run "Build" 42)
                  (a-builds-properties 987 {})
                  (a-file-list "Build" 42 "SomeMore" "1" "SomeJob")
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (= ["/builds/Build%20%3A%3A%20SomeMore/42%201"]
             (map first @store)))))

  (testing "should sync test results"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "AlphaJob" 1493201298062 321)))
                  (a-pipeline-run "Build" 42
                                  (a-material-revision "AnotherPipeline/21" 7))
                  (a-builds-properties 321 {})
                  (a-file-list "Build" 42 "DoStuff" "1" "AlphaJob"
                               {:files [{:name "dontcare.log"
                                         :url "http://example.com/something/files/Build/42/DoStuff/1/AlphaJob/tmp/dontcare.log"}
                                        {:name "results.xml"
                                         :url "http://example.com/something/files/Build/42/DoStuff/1/AlphaJob/tmp/results.xml"}]})
                  (a-file "Build" 42 "DoStuff" "1" "AlphaJob" "tmp/results.xml"
                          "<testsuites></testsuites>")
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (= [["/builds/Build%20%3A%3A%20DoStuff/42%201/testresults" "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites></testsuites>"]]
             (filter (fn [[path payload]] (= path "/builds/Build%20%3A%3A%20DoStuff/42%201/testresults"))
                     @store)))))

  (testing "should sync multiple test results in one job"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "AlphaJob" 1493201298062 321)))
                  (a-pipeline-run "Build" 42
                                  (a-material-revision "AnotherPipeline/21" 7))
                  (a-builds-properties 321 {})
                  (a-file-list "Build" 42 "DoStuff" "1" "AlphaJob"
                               {:name "one_result.xml"
                                :url "http://example.com/something/files/Build/42/DoStuff/1/AlphaJob/one_result.xml"}
                               {:name "others.xml"
                                :url "http://example.com/something/files/Build/42/DoStuff/1/AlphaJob/others.xml"})
                  (a-file "Build" 42 "DoStuff" "1" "AlphaJob" "one_result.xml"
                          "<testsuite name=\"one\"></testsuite>")
                  (a-file "Build" 42 "DoStuff" "1" "AlphaJob" "others.xml"
                          "<testsuites><testsuite name=\"other\"></testsuite></testsuites>")
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (= [["/builds/Build%20%3A%3A%20DoStuff/42%201/testresults"
               "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"one\"></testsuite><testsuite name=\"other\"></testsuite></testsuites>"]]
             (filter (fn [[path payload]] (= path "/builds/Build%20%3A%3A%20DoStuff/42%201/testresults"))
                     @store)))))

  (testing "should combine test results for two jobs"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "AlphaJob" 1493201298062 321)
                                                (a-job-run "BetaJob" 1493201298062 987)))
                  (a-pipeline-run "Build" 42
                                  (a-material-revision "AnotherPipeline/21" 7))
                  (a-builds-properties 321 {})
                  (a-builds-properties 987 {})
                  (a-file-list "Build" 42 "DoStuff" "1" "AlphaJob"
                               {:files [{:name "results.xml"
                                         :url "http://example.com/something/files/Build/42/DoStuff/1/AlphaJob/tmp/results.xml"}]})
                  (a-file-list "Build" 42 "DoStuff" "1" "BetaJob"
                               {:files [{:name "results.xml"
                                         :url "http://example.com/something/files/Build/42/DoStuff/1/BetaJob/tmp/results.xml"}]})
                  (a-file "Build" 42 "DoStuff" "1" "AlphaJob" "tmp/results.xml"
                          "<testsuites><testsuite name=\"Alpha\"></testsuite></testsuites>")
                  (a-file "Build" 42 "DoStuff" "1" "BetaJob" "tmp/results.xml"
                          "<testsuites><testsuite name=\"Beta\"></testsuite></testsuites>")
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (= [["/builds/Build%20%3A%3A%20DoStuff/42%201/testresults"
               "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"Alpha\"></testsuite><testsuite name=\"Beta\"></testsuite></testsuites>"]]
             (filter (fn [[path payload]] (= path "/builds/Build%20%3A%3A%20DoStuff/42%201/testresults"))
                     @store)))))

  (testing "should not store test results if one job has invalid XML"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "AlphaJob" 1493201298062 321)
                                                (a-job-run "BetaJob" 1493201298062 987)))
                  (a-pipeline-run "Build" 42
                                  (a-material-revision "AnotherPipeline/21" 7))
                  (a-builds-properties 321 {})
                  (a-builds-properties 987 {})
                  (a-file-list "Build" 42 "DoStuff" "1" "AlphaJob"
                               {:files [{:name "results.xml"
                                         :url "http://example.com/something/files/Build/42/DoStuff/1/AlphaJob/tmp/results.xml"}]})
                  (a-file-list "Build" 42 "DoStuff" "1" "BetaJob"
                               {:files [{:name "results.xml"
                                         :url "http://example.com/something/files/Build/42/DoStuff/1/BetaJob/tmp/results.xml"}]})
                  (a-file "Build" 42 "DoStuff" "1" "AlphaJob" "tmp/results.xml"
                          "<testsuites><testsuite name=\"Alpha\"></testsuite></testsuites>")
                  (a-file "Build" 42 "DoStuff" "1" "BetaJob" "tmp/results.xml"
                          "invalid xml")
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (= []
             (filter (fn [[path payload]] (= path "/builds/Build%20%3A%3A%20DoStuff/42%201/testresults"))
                     @store)))))

  (testing "should store test results even if one job has no XML"
    (let [store (atom [])]
      (fake/with-fake-routes-in-isolation
        (serve-up (a-config (a-pipeline-group "Development"
                                              (a-pipeline "Build"
                                                          (a-stage "DoStuff"))))
                  (a-short-history "Build" "DoStuff"
                                   (a-stage-run 42 "1" "Passed"
                                                (a-job-run "AlphaJob" 1493201298062 321)
                                                (a-job-run "BetaJob" 1493201298062 987)))
                  (a-pipeline-run "Build" 42
                                  (a-material-revision "AnotherPipeline/21" 7))
                  (a-builds-properties 321 {})
                  (a-builds-properties 987 {})
                  (a-file-list "Build" 42 "DoStuff" "1" "AlphaJob"
                               {:files [{:name "results.xml"
                                         :url "http://example.com/something/files/Build/42/DoStuff/1/AlphaJob/tmp/results.xml"}]})
                  (a-file-list "Build" 42 "DoStuff" "1" "BetaJob")
                  (a-file "Build" 42 "DoStuff" "1" "AlphaJob" "tmp/results.xml"
                          "<testsuites><testsuite name=\"Alpha\"></testsuite></testsuites>")
                  (provide-buildviz-and-capture-puts store))
        (with-out-str (sut/sync-stages (url/url "http://gocd:8513")
                                       (url/url "http://buildviz:8010")
                                       beginning-of-2016 nil nil)))
      (is (= [["/builds/Build%20%3A%3A%20DoStuff/42%201/testresults"
               "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"Alpha\"></testsuite></testsuites>"]]
             (filter (fn [[path payload]] (= path "/builds/Build%20%3A%3A%20DoStuff/42%201/testresults"))
                     @store))))))
