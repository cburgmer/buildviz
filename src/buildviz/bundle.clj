(ns buildviz.bundle
  (:gen-class)
  (:require [buildviz.data.results :as results]
            [buildviz.controllers
             [builds :refer [get-builds]]
             [fail-phases :refer [get-fail-phases]]
             [flaky-testcases :refer [get-flaky-testclasses]]
             [wait-times :refer [get-wait-times]]
             [jobs :refer [get-jobs]]
             [pipeline-runtime :refer [get-pipeline-runtime]]
             [status :refer [get-status]]
             [testcases :refer [get-testcases]]
             [testclasses :refer [get-testclasses]]]
            [buildviz.storage :as storage]
            [clojure.java.io :as io]))

(def ^:private data-dir (if-let [data-dir (System/getenv "BUILDVIZ_DATA_DIR")]
                          data-dir
                          "data/"))

(def ^:private pipeline-name (System/getenv "BUILDVIZ_PIPELINE_NAME"))

(defn- store-response [file response]
  (spit file (:body response)))

(defn- copy-statics [from target]
  (let [out-file (io/file target from)]
    (.mkdirs (io/file (.getParent out-file)))
    (spit out-file (slurp (io/resource (clojure.string/join "/" ["public" from]))))))

(defn -main [target-path]
  (let [target (io/file target-path)
        builds (storage/load-builds data-dir)
        build-results (results/build-results builds
                                             (partial storage/load-testresults data-dir)
                                             #()
                                             #())
        accept {:mime :json}]
    (.mkdirs target)
    (store-response (io/file target "builds") (get-builds build-results accept 0))
    (store-response (io/file target "status") (get-status build-results pipeline-name))
    (store-response (io/file target "jobs") (get-jobs build-results accept 0))
    (store-response (io/file target "pipelineruntime") (get-pipeline-runtime build-results accept 0))
    (store-response (io/file target "waittimes") (get-wait-times build-results accept 0))
    (store-response (io/file target "failphases") (get-fail-phases build-results accept 0))
    (store-response (io/file target "testcases") (get-testcases build-results accept 0))
    (store-response (io/file target "testclasses") (get-testclasses build-results accept 0))
    (store-response (io/file target "flakytestcases") (get-flaky-testclasses build-results accept 0))

    (dorun (->> '("index.html"
                  "status.css"
                  "status.js"
                  "common/badJobs.css"
                  "common/base.css"
                  "common/events.css"
                  "common/graphDescription.css"
                  "common/graphFactory.css"
                  "common/jobColors.js"
                  "common/timespanSelection.js"
                  "common/tooltip.js"
                  "common/weightedTimeline.css"
                  "common/zoomableSunburst.css"
                  "common/badJobs.js"
                  "common/dataSource.js"
                  "common/events.js"
                  "common/graphDescription.js"
                  "common/graphFactory.js"
                  "common/timespanSelection.css"
                  "common/tooltip.css"
                  "common/utils.js"
                  "common/weightedTimeline.js"
                  "common/zoomableSunburst.js"
                  "graphs/averageJobRuntime.css"
                  "graphs/averageJobRuntime.js"
                  "graphs/averageTestRuntime.js"
                  "graphs/builds.js"
                  "graphs/failPhases.css"
                  "graphs/failPhases.js"
                  "graphs/failedBuilds.js"
                  "graphs/flakyBuilds.js"
                  "graphs/flakyTests.js"
                  "graphs/jobWaitTimes.js"
                  "graphs/mostFrequentlyFailingTests.js"
                  "graphs/pipelineRuntime.js"
                  "graphs/slowestTests.js"
                  "vendor/d3.min.js"
                  "vendor/moment-duration-format.js"
                  "vendor/moment.js")
                (map #(copy-statics % target))))

    (println "OK")))
