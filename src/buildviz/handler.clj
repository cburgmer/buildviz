(ns buildviz.handler
  (:use
        ring.middleware.json
        ring.middleware.resource
        ring.middleware.content-type
        ring.middleware.not-modified
        ring.middleware.accept
        ring.util.response
        buildviz.util
        buildviz.storage
        [compojure.core :only (GET PUT)]
        [clojure.string :only (join escape)])
  (:require [compojure.handler :as handler]
            [buildviz.csv :as csv]
            [buildviz.jobinfo :as jobinfo]
            [buildviz.pipelineinfo :as pipelineinfo]
            [buildviz.testsuites :as testsuites]
            [closchema.core :as schema]))

(def jobs-filename "buildviz_jobs")

(def build-schema {:type "object"
                   :properties {:start {:type "integer"}
                                :end {:type "integer"}
                                :outcome {:enum ["pass" "fail"]}
                                :inputs {:type "array"
                                         :items {:type "object"}}}
                   :additionalProperties false})

(def test-results (atom {}))

(defn- job-entry [job builds]
  (if (contains? @builds job)
    (@builds job)
    {}))

(defn- test-results-entry [job]
  (if (contains? @test-results job)
    (@test-results job)
    {}))

(defn- build-data-validation-errors [build-data]
  (schema/report-errors (schema/validate build-schema build-data)))

(defn- do-store-build! [builds job build build-data persist-jobs!]
  (let [entry (job-entry job builds)
        updated-entry (assoc entry build build-data)]
    (swap! builds assoc job updated-entry)
    (persist-jobs! @builds jobs-filename)
    (respond-with-json build-data)))

(defn- store-build! [builds job build build-data persist-jobs!]
  (if-let [errors (seq (build-data-validation-errors build-data))]
    {:status 400
     :body errors}
    (do-store-build! builds job build build-data persist-jobs!)))

(defn- get-build [builds job build]
  (if (contains? @builds job)
    (if-let [build-data ((@builds job) build)]
      (respond-with-json build-data)
      {:status 404})
    {:status 404}))

(defn- store-test-results! [job build body]
  (let [content (slurp body)]
    (try
      (testsuites/testsuites-for content) ; try parse

      (let [entry (test-results-entry job)
            updated-entry (assoc entry build content)]
        (swap! test-results assoc job updated-entry)
        {:status 204})
      (catch Exception e
        {:status 400}))))

(defn- get-test-results [job build accept]
  (if-let [job-results (@test-results job)]
    (if-let [content (job-results build)]
      (if (= (:mime accept) :json)
        (respond-with-json (testsuites/testsuites-for content))
        (respond-with-xml content))
      {:status 404})
    {:status 404}))

(defn- serialize-nested-testsuites [testsuite-id]
  (join ": " testsuite-id))

;; jobs

(defn- average-runtime-for [build-data-entries]
  (if-let [avg-runtime (jobinfo/average-runtime build-data-entries)]
    {:averageRuntime avg-runtime}))

(defn- total-count-for [build-data-entries]
  {:totalCount (count build-data-entries)})

(defn- failed-count-for [build-data-entries]
  (if-let [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:failedCount (jobinfo/fail-count builds)}))

(defn- flaky-count-for [build-data-entries]
  (if-let [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:flakyCount (jobinfo/flaky-build-count builds)}))

(defn- summary-for [builds job]
  (let [build-data-entries (vals (@builds job))]
    (merge (average-runtime-for build-data-entries)
           (total-count-for build-data-entries)
           (failed-count-for build-data-entries)
           (flaky-count-for build-data-entries))))

(defn- get-jobs [builds accept]
  (let [jobNames (keys @builds)
        buildSummaries (map #(summary-for builds %) jobNames)
        buildSummary (zipmap jobNames buildSummaries)]
    (if (= (:mime accept) :json)
      (respond-with-json buildSummary)
      (respond-with-csv
       (csv/export-table ["job" "averageRuntime" "totalCount" "failedCount" "flakyCount"]
                         (map (fn [[job-name job]] [job-name
                                                    (csv/format-duration (:averageRuntime job))
                                                    (:totalCount job)
                                                    (:failedCount job)
                                                    (:flakyCount job)])
                              buildSummary))))))

;; pipelineruntime

(defn- runtimes-by-day-for [builds job]
  (let [build-data-entries (vals (@builds job))]
    (jobinfo/average-runtime-by-day build-data-entries)))

(defn- runtimes-by-day [builds]
  (let [job-names (keys @builds)]
    (->> (map #(runtimes-by-day-for builds %) job-names)
         (zipmap job-names)
         (filter #(not-empty (second %))))))

(defn- remap-date-first [[job runtimes-by-day]]
  (map (fn [[day avg-runtime]]
              [day {job avg-runtime}])
            runtimes-by-day))

(defn- merge-runtimes [all-runtimes-by-day]
  (->> (mapcat remap-date-first all-runtimes-by-day)
       (group-by first)
       (map (fn [[date entries]]
              [date (apply merge (map second entries))]))))

(defn- runtime-table-entry [date runtimes job-names]
  (->> (map #(get runtimes %) job-names)
       (map csv/format-duration)
       (cons date)))

(defn- runtimes-as-table [job-names runtimes]
  (map (fn [[date runtimes-by-day]]
         (runtime-table-entry date runtimes-by-day job-names))
       runtimes))

(defn- get-pipeline-runtime [builds]
  (let [runtimes-by-day (runtimes-by-day builds)
        job-names (keys runtimes-by-day)]

    (respond-with-csv (csv/export-table (cons "date" job-names)
                                        (->> (merge-runtimes runtimes-by-day)
                                             (runtimes-as-table job-names)
                                             (sort-by first))))))

;; fail phases

(defn- all-builds-in-order [builds]
  (mapcat (fn [[job builds]]
            (map #(assoc % :job job) (vals builds)))
          @builds))

(defn- get-fail-phases [builds accept]
  (let [annotated-builds-in-order (sort-by :end (all-builds-in-order builds))
        fail-phases (pipelineinfo/pipeline-fail-phases annotated-builds-in-order)]
    (if (= (:mime accept) :json)
      (respond-with-json fail-phases)
      (respond-with-csv
       (csv/export-table ["start" "end" "culprits"]
                         (map (fn [{start :start end :end culprits :culprits}]
                                [(csv/format-timestamp start)
                                 (csv/format-timestamp end)
                                 (join "|" culprits)])
                              fail-phases))))))

;; failures

(defn- failures-for [builds job]
  (when-let [test-results (@test-results job)]
    (when-let [failed-tests (seq (testsuites/accumulate-testsuite-failures
                                  (map testsuites/testsuites-for (vals test-results))))]
      (let [build-data-entries (vals (@builds job))]
        {job (merge {:children failed-tests}
                    (failed-count-for build-data-entries))}))))

(defn- failures-as-list [job]
  (when-let [test-results (@test-results job)]
    (->> (map testsuites/testsuites-for (vals test-results))
         (testsuites/accumulate-testsuite-failures-as-list)
         (map (fn [{testsuite :testsuite classname :classname name :name failed-count :failedCount}]
                [failed-count
                 job
                 (serialize-nested-testsuites testsuite)
                 classname
                 name])))))

(defn- get-failures [builds accept]
  (let [jobs (keys @builds)]
    (if (= (:mime accept) :json)
      (let [failures (map #(failures-for builds %) jobs)]
        (respond-with-json (into {} (apply merge failures))))
      (respond-with-csv (csv/export-table ["failedCount" "job" "testsuite" "classname" "name"]
                                          (mapcat failures-as-list jobs))))))

;; testsuites

(defn- test-runs [job]
  (let [test-results (@test-results job)]
    (map testsuites/testsuites-for (vals test-results))))

(defn- testsuites-for [job]
  {:children (testsuites/average-testsuite-runtime (test-runs job))})

(defn- flat-test-runtimes [job]
  (->> (testsuites/average-testsuite-runtime-as-list (test-runs job))
       (map (fn [{testsuite :testsuite classname :classname name :name average-runtime :averageRuntime}]
              [(csv/format-duration average-runtime)
               job
               (serialize-nested-testsuites testsuite)
               classname
               name]))))

(defn- has-testsuites? [job]
  (some? (@test-results job)))

(defn- get-testsuites [builds accept]
  (let [job-names (filter has-testsuites? (keys @builds))]
    (if (= (:mime accept) :json)
      (respond-with-json (zipmap job-names (map testsuites-for job-names)))
      (respond-with-csv (csv/export-table
                         ["averageRuntime" "job" "testsuite" "classname" "name"]
                         (mapcat flat-test-runtimes job-names))))))

;; app

(defn- app-routes [builds persist-jobs!]
  (compojure.core/routes
   (GET "/" [] (redirect "/index.html"))

   (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build! builds job build body persist-jobs!))
   (GET "/builds/:job/:build" [job build] (get-build builds job build))
   (PUT "/builds/:job/:build/testresults" [job build :as {body :body}] (store-test-results! job build body))
   (GET "/builds/:job/:build/testresults" [job build :as {accept :accept}] (get-test-results job build accept))

   (GET "/jobs" {accept :accept} (get-jobs builds accept))
   (GET "/jobs.csv" {} (get-jobs builds {:mime :csv}))
   (GET "/pipelineruntime" {} (get-pipeline-runtime builds))
   (GET "/failphases" {accept :accept} (get-fail-phases builds accept))
   (GET "/failphases.csv" {} (get-fail-phases builds {:mime :csv}))
   (GET "/failures" {accept :accept} (get-failures builds accept))
   (GET "/failures.csv" {} (get-failures builds {:mime :csv}))
   (GET "/testsuites" {accept :accept} (get-testsuites builds accept))
   (GET "/testsuites.csv" {} (get-testsuites builds {:mime :csv}))))

(defn create-app [builds persist-jobs!]
  (-> (app-routes builds store-jobs!)
      wrap-json-response
      (wrap-json-body {:keywords? true})
      (wrap-accept {:mime ["application/json" :as :json,
                           "application/xml" "text/xml" :as :xml
                           "text/plain" :as :plain]})
      (wrap-resource "public")
      wrap-content-type
      wrap-not-modified))

(def app
  (let [builds (atom (load-jobs jobs-filename))]
    (create-app builds store-jobs!)))
