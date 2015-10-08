(ns buildviz.handler
  (:require [buildviz
             [build-results :as results]
             [csv :as csv]
             [http :as http]
             [jobinfo :as jobinfo]
             [junit-xml :as junit-xml]
             [pipelineinfo :as pipelineinfo]
             [testsuites :as testsuites]]
            [clojure
             [string :as str]
             [walk :as walk]]
            [compojure.core :as compojure :refer :all]
            [ring.middleware
             [accept :as accept]
             [content-type :as content-type]
             [json :as json]
             [not-modified :as not-modified]
             [params :as params]
             [resource :as resources]]
            [ring.util.response :as response]))

(defn- store-build! [build-results job-name build-id build-data]
  (if-some [errors (seq (results/build-data-validation-errors build-data))]
    {:status 400
     :body errors}
    (do (results/set-build! build-results job-name build-id build-data)
        (http/respond-with-json build-data))))

(defn- get-build [build-results job-name build-id]
  (if-some [build-data (results/build build-results job-name build-id)]
    (http/respond-with-json build-data)
    {:status 404}))

(defn- force-evaluate-junit-xml [content]
  (walk/postwalk identity (junit-xml/parse-testsuites content)))

(defn- store-test-results! [build-results job-name build-id body]
  (let [content (slurp body)]
    (try
      (force-evaluate-junit-xml content)
      (results/set-tests! build-results job-name build-id content)
      {:status 204}
      (catch Exception e
        {:status 400
         :body (.getMessage e)}))))

(defn- get-test-results [build-results job-name build-id accept]
  (if-some [content (results/tests build-results job-name build-id)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (junit-xml/parse-testsuites content))
      (http/respond-with-xml content))
    {:status 404}))

(defn- serialize-nested-testsuites [testsuite-id]
  (str/join ": " testsuite-id))

;; status

(defn- with-latest-build-start [all-builds response]
  (if-let [build-starts (seq (remove nil? (map :start all-builds)))]
    (assoc response :latestBuildStart (apply max build-starts))
    response))

(defn- get-status [build-results]
  (let [all-builds (seq (mapcat #(results/builds build-results %)
                                (results/job-names build-results)))
        total-build-count (count all-builds)]
    (http/respond-with-json (with-latest-build-start all-builds
                              {:totalBuildCount total-build-count}))))

;; jobs

(defn- average-runtime-for [build-data-entries]
  (if-let [avg-runtime (jobinfo/average-runtime build-data-entries)]
    {:averageRuntime avg-runtime}))

(defn- total-count-for [build-data-entries]
  {:totalCount (count build-data-entries)})

(defn- failed-count-for [build-data-entries]
  (if-some [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:failedCount (jobinfo/fail-count builds)}))

(defn- flaky-count-for [build-data-entries]
  (if-some [builds (seq (jobinfo/builds-with-outcome build-data-entries))]
    {:flakyCount (jobinfo/flaky-build-count builds)}))

(defn- summary-for [build-results job-name]
  (let [build-data-entries (results/builds build-results job-name)]
    (merge (average-runtime-for build-data-entries)
           (total-count-for build-data-entries)
           (failed-count-for build-data-entries)
           (flaky-count-for build-data-entries))))

(defn- get-jobs [build-results accept]
  (let [job-names (results/job-names build-results)
        build-summaries (map #(summary-for build-results %) job-names)
        build-summary (zipmap job-names build-summaries)]
    (if (= (:mime accept) :json)
      (http/respond-with-json build-summary)
      (http/respond-with-csv
       (csv/export-table ["job" "averageRuntime" "totalCount" "failedCount" "flakyCount"]
                         (map (fn [[job-name job]] [job-name
                                                    (csv/format-duration (:averageRuntime job))
                                                    (:totalCount job)
                                                    (:failedCount job)
                                                    (:flakyCount job)])
                              build-summary))))))

;; pipelineruntime

(defn- runtimes-by-day [build-results]
  (let [job-names (results/job-names build-results)]
    (->> (map #(jobinfo/average-runtime-by-day (results/builds build-results %))
              job-names)
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

(defn- get-pipeline-runtime [build-results]
  (let [runtimes-by-day (runtimes-by-day build-results)
        job-names (keys runtimes-by-day)]

    (http/respond-with-csv (csv/export-table (cons "date" job-names)
                                             (->> (merge-runtimes runtimes-by-day)
                                                  (runtimes-as-table job-names)
                                                  (sort-by first))))))

;; fail phases

(defn- all-builds-in-order [build-results]
  (sort-by :end
           (mapcat (fn [[job builds]]
                     (map #(assoc % :job job) (vals builds)))
                   @(:builds build-results))))

(defn- get-fail-phases [build-results accept]
  (let [fail-phases (pipelineinfo/pipeline-fail-phases (all-builds-in-order build-results))]
    (if (= (:mime accept) :json)
      (http/respond-with-json fail-phases)
      (http/respond-with-csv
       (csv/export-table ["start" "end" "culprits"]
                         (map (fn [{start :start end :end culprits :culprits}]
                                [(csv/format-timestamp start)
                                 (csv/format-timestamp end)
                                 (str/join "|" culprits)])
                              fail-phases))))))

;; failures

(defn- test-runs [build-results job-name from-timestamp]
  (map junit-xml/parse-testsuites
       (results/chronological-tests build-results job-name from-timestamp)))

(defn- failures-for [build-results job-name from-timestamp]
  (when-some [failed-tests (seq (testsuites/accumulate-testsuite-failures (test-runs build-results job-name from-timestamp)))]
    (let [build-data-entries (results/builds build-results job-name)]
      {job-name (merge {:children failed-tests}
                       (failed-count-for build-data-entries))})))

(defn- failures-as-list [build-results job-name from-timestamp]
  (when-some [test-results (seq (test-runs build-results job-name from-timestamp))]
    (->> test-results
         (testsuites/accumulate-testsuite-failures-as-list)
         (map (fn [{testsuite :testsuite classname :classname name :name failed-count :failedCount}]
                [failed-count
                 job-name
                 (serialize-nested-testsuites testsuite)
                 classname
                 name])))))

(defn- get-failures [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)]
    (if (= (:mime accept) :json)
      (let [failures (map #(failures-for build-results % from-timestamp) job-names)]
        (http/respond-with-json (into {} (apply merge failures))))
      (http/respond-with-csv (csv/export-table ["failedCount" "job" "testsuite" "classname" "name"]
                                               (mapcat #(failures-as-list build-results % from-timestamp) job-names))))))

;; testsuites

(defn- testcase-runtimes [build-results job-name from-timestamp]
  {:children (testsuites/average-testcase-runtime (test-runs build-results job-name from-timestamp))})

(defn- flat-testcase-runtimes [build-results job-name from-timestamp]
  (->> (testsuites/average-testcase-runtime-as-list (test-runs build-results job-name from-timestamp))
       (map (fn [{testsuite :testsuite classname :classname name :name average-runtime :averageRuntime}]
              [(csv/format-duration average-runtime)
               job-name
               (serialize-nested-testsuites testsuite)
               classname
               name]))))

(defn- get-testcases [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> job-names
                                   (map #(testcase-runtimes build-results % from-timestamp))
                                   (zipmap job-names)
                                   (remove (fn [[job-name testcases]] (empty? (:children testcases))))
                                   (into {})))
      (http/respond-with-csv (csv/export-table
                              ["averageRuntime" "job" "testsuite" "classname" "name"]
                              (mapcat #(flat-testcase-runtimes build-results % from-timestamp) job-names))))))

;; testclasses

(defn- testclass-runtimes [build-results job-name from-timestamp]
  {:children (testsuites/average-testclass-runtime (test-runs build-results job-name from-timestamp))})

(defn- flat-testclass-runtimes [build-results job-name from-timestamp]
  (->> (testsuites/average-testclass-runtime-as-list (test-runs build-results job-name from-timestamp))
       (map (fn [{testsuite :testsuite classname :classname average-runtime :averageRuntime}]
              [(csv/format-duration average-runtime)
               job-name
               (serialize-nested-testsuites testsuite)
               classname]))))

(defn- get-testclasses [build-results accept from-timestamp]
  (let [job-names (results/job-names build-results)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> job-names
                                   (map #(testclass-runtimes build-results % from-timestamp))
                                   (zipmap job-names)
                                   (remove (fn [[job-name testcases]] (empty? (:children testcases))))
                                   (into {})))
      (http/respond-with-csv (csv/export-table
                              ["averageRuntime" "job" "testsuite" "classname"]
                              (mapcat #(flat-testclass-runtimes build-results % from-timestamp) job-names))))))

;; flaky testcases

(defn- test-results-for-build [build-results job-name build-id]
  (if-let [test-results (results/tests build-results job-name build-id)]
    (junit-xml/parse-testsuites test-results)))

(defn- builds-after-timestamp [build-results job-name from-timestamp]
  (let [builds (get @(:builds build-results) job-name)]
    (if-not (nil? from-timestamp)
      (->> builds
           (remove (fn [[build-id build]] (nil? (:start build))))
           (filter (fn [[build-id build]] (>= (:start build) from-timestamp)))
           (into {}))
      builds)))

(defn- flat-flaky-testcases [build-results job-name from-timestamp]
  (let [builds (builds-after-timestamp build-results job-name from-timestamp)
        test-lookup (partial test-results-for-build build-results job-name)]
    (->> (testsuites/flaky-testcases-as-list builds test-lookup)
         (map (fn [{testsuite :testsuite
                    classname :classname
                    name :name
                    build-id :build-id
                    latest-failure :latest-failure
                    flaky-count :flaky-count}]
                [(csv/format-timestamp latest-failure)
                 flaky-count
                 job-name
                 build-id
                 (serialize-nested-testsuites testsuite)
                 classname
                 name])))))

(defn- get-flaky-testclasses [build-results from-timestamp]
  (http/respond-with-csv (csv/export-table
                          ["latestFailure" "flakyCount" "job" "latestBuildId" "testsuite" "classname" "name"]
                          (mapcat #(flat-flaky-testcases build-results % from-timestamp)
                                  (results/job-names build-results)))))

;; app

(defn- from-timestamp [{from "from"}]
  (when from
    (Long. from)))

(defn- app-routes [build-results]
  (compojure/routes
   (GET "/" [] (response/redirect "/index.html"))

   (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build! build-results job build body))
   (GET "/builds/:job/:build" [job build] (get-build build-results job build))
   (PUT "/builds/:job/:build/testresults" [job build :as {body :body}] (store-test-results! build-results job build body))
   (GET "/builds/:job/:build/testresults" [job build :as {accept :accept}] (get-test-results build-results job build accept))

   (GET "/status" {} (get-status build-results))
   (GET "/jobs" {accept :accept} (get-jobs build-results accept))
   (GET "/jobs.csv" {} (get-jobs build-results {:mime :csv}))
   (GET "/pipelineruntime" {} (get-pipeline-runtime build-results))
   (GET "/pipelineruntime.csv" {} (get-pipeline-runtime build-results))
   (GET "/failphases" {accept :accept} (get-fail-phases build-results accept))
   (GET "/failphases.csv" {} (get-fail-phases build-results {:mime :csv}))
   (GET "/failures" {accept :accept query :query-params} (get-failures build-results accept (from-timestamp query)))
   (GET "/failures.csv" {query :query-params} (get-failures build-results {:mime :csv} (from-timestamp query)))
   (GET "/testcases" {accept :accept query :query-params} (get-testcases build-results accept (from-timestamp query)))
   (GET "/testcases.csv" {query :query-params} (get-testcases build-results {:mime :csv} (from-timestamp query)))
   (GET "/testclasses" {accept :accept query :query-params} (get-testclasses build-results accept (from-timestamp query)))
   (GET "/testclasses.csv" {query :query-params} (get-testclasses build-results {:mime :csv} (from-timestamp query)))
   (GET "/flakytestcases" {query :query-params} (get-flaky-testclasses build-results (from-timestamp query)))
   (GET "/flakytestcases.csv" {query :query-params} (get-flaky-testclasses build-results (from-timestamp query)))))

(defn create-app [build-results]
  (-> (app-routes build-results)
      params/wrap-params
      json/wrap-json-response
      (json/wrap-json-body {:keywords? true})
      (accept/wrap-accept {:mime ["application/json" :as :json,
                           "application/xml" "text/xml" :as :xml
                           "text/plain" :as :plain]})
      (resources/wrap-resource "public")
      content-type/wrap-content-type
      not-modified/wrap-not-modified))
