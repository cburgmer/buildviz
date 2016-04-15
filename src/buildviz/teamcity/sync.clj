(ns buildviz.teamcity.sync
  (:gen-class)
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
             [format :as tf]
             [local :as l]]
            [clojure.string :as string]
            [clojure.tools
             [cli :refer [parse-opts]]
             [logging :as log]]))


(def tz (t/default-time-zone))

(def date-formatter (tf/formatter tz "YYYY-MM-dd" "YYYY/MM/dd" "YYYYMMdd" "dd.MM.YYYY"))

(def cli-options
  [["-b" "--buildviz URL" "URL pointing to a running buildviz instance"
    :id :buildviz-url
    :default "http://localhost:3000"]
   ["-p" "--project PROJECT" "TeamCity project to be synced"
    :id :projects
    :default []
    :assoc-fn (fn [previous key val] (assoc previous key (conj (get previous key) val)))]
   ["-f" "--from DATE" "Date from which on builds are loaded, if not specified tries to pick up where the last run finished"
    :id :sync-start-time
    :parse-fn #(tf/parse date-formatter %)]
   ["-h" "--help"]])

(defn usage [options-summary]
  (string/join "\n"
               [""
                "Syncs TeamCity build history with buildviz"
                ""
                "Usage: buildviz.teamcity.sync [OPTIONS] TEAMCITY_URL"
                ""
                "TEAMCITY_URL           The URL of the TeamCity installation. You will most probably"
                "                       need some form of credentials. If 'guest user login' is"
                "                       enabled, you can try e.g. 'http://guest@localhost:8111'."
                ""
                "Options"
                options-summary]))


(defn- all-builds-for-job [teamcity-url sync-start-time {:keys [id projectName name]}]
  (let [safe-build-start-time (t/minus sync-start-time (t/millis 1))]
    (->> (api/get-builds teamcity-url id)
         (map (fn [build]
                {:build build
                 :project-name projectName
                 :job-name name}))
         (take-while #(t/after? (transform/parse-build-date (get-in % [:build :startDate])) safe-build-start-time)))))

(defn add-test-results [teamcity-url build]
  (assoc build :tests (api/get-test-report teamcity-url (:id (:build build)))))

(defn- put-build [buildviz-url job-name build-id build]
  (client/put (string/join [(url/with-plain-text-password buildviz-url) (format "/builds/%s/%s" job-name build-id)])
              {:content-type :json
               :body (json/to-string build)}))

(defn put-test-results [buildviz-url job-name build-id test-results]
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

(def last-week (t/minus (.withTimeAtStartOfDay (l/local-now)) (t/weeks 1)))

(defn- assert-parameter [assert-func msg]
  (when (not (assert-func))
    (println msg)
    (System/exit 1)))

(defn -main [& c-args]
  (let [args (parse-opts c-args cli-options)]
    (when (:help (:options args))
      (println (usage (:summary args)))
      (System/exit 0))
    (when (:errors args)
      (println (string/join "\n" (:errors args)))
      (System/exit 1))

    (let [teamcity-url (url/url (first (:arguments args)))
          buildviz-url (url/url (:buildviz-url (:options args)))
          projects (:projects (:options args))
          user-sync-start (:sync-start-time (:options args))]

      (assert-parameter #(some? teamcity-url) "The URL of TeamCity is required. Try --help.")
      (assert-parameter #(not (empty? projects)) "At least one project is required. Try --help.")

      (sync-jobs teamcity-url buildviz-url projects last-week user-sync-start))))
