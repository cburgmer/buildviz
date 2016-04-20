(ns buildviz.teamcity.sync-jobs
  (:require [buildviz.teamcity
             [api :as api]
             [transform :as transform]]
            [buildviz.util
             [json :as json]
             [url :as url]]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-progress.core :as progress]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- all-builds-for-job [teamcity-url sync-start-time {:keys [id projectName name]}]
  (let [safe-build-start-time (t/minus sync-start-time (t/millis 1))]
    (->> (api/get-builds teamcity-url id)
         (map (fn [build]
                {:build build
                 :project-name projectName
                 :job-name name}))
         (take-while #(t/after? (transform/parse-build-date (get-in % [:build :startDate])) safe-build-start-time)))))

(defn- add-test-results [teamcity-url build]
  (assoc build :tests (api/get-test-report teamcity-url (:id (:build build)))))


(defn- put-build [buildviz-url job-name build-id build]
  (client/put (string/join [(url/with-plain-text-password buildviz-url) (format "/builds/%s/%s" job-name build-id)])
              {:content-type :json
               :body (json/to-string build)}))

(defn- put-test-results [buildviz-url job-name build-id test-results]
  (client/put (string/join [(url/with-plain-text-password buildviz-url) (format "/builds/%s/%s/testresults" job-name build-id)])
              {:content-type :json
               :body (j/generate-string test-results)}))

(defn- put-to-buildviz [buildviz-url {:keys [job-name build-id build test-results]}]
  (log/info (format "Syncing %s %s: build" job-name build-id))
  (put-build buildviz-url job-name build-id build)
  (when-not (empty? test-results)
    (put-test-results buildviz-url job-name build-id test-results)))


(defn- sync-oldest-first-to-deal-with-cancellation [builds]
  (sort-by #(get-in % [:build :finishDate]) builds))

(defn- ignore-ongoing-builds [builds]
  (filter :result builds))

(defn- stop-at-first-non-finished-so-we-can-resume-later [builds]
  (take-while #(= "finished" (get-in % [:build :state])) builds))

(defn- get-latest-synced-build-start [buildviz-url]
  (let [response (client/get (format "%s/status" buildviz-url))
        buildviz-status (j/parse-string (:body response) true)]
    (when-let [latest-build-start (:latestBuildStart buildviz-status)]
      (tc/from-long latest-build-start))))

(defn- sync-start [buildviz-url default-sync-start user-sync-start]
  (let [last-sync-date (get-latest-synced-build-start buildviz-url)]
    (or user-sync-start
        last-sync-date
        default-sync-start)))


(defn sync-jobs [teamcity-url buildviz-url projects default-sync-start user-sync-start]
  (println "TeamCity" (str teamcity-url) projects "-> buildviz" (str buildviz-url))
  (let [sync-start-time (sync-start buildviz-url default-sync-start user-sync-start)]
    (println (format "Finding all builds for syncing (starting from %s)..."
                     (tf/unparse (:date-time tf/formatters) sync-start-time)))
    (->> projects
         (mapcat #(api/get-jobs teamcity-url %))
         (mapcat #(all-builds-for-job teamcity-url sync-start-time %))
         (progress/init "Syncing")
         sync-oldest-first-to-deal-with-cancellation
         stop-at-first-non-finished-so-we-can-resume-later
         (map (comp progress/tick
                    (partial put-to-buildviz buildviz-url)
                    transform/teamcity-build->buildviz-build
                    (partial add-test-results teamcity-url)))
         dorun
         (progress/done))))
