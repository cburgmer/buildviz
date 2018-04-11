(ns buildviz.go.sync
  (:gen-class)
  (:require [buildviz.go.sync-jobs :as sync-jobs]
            [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as l]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]))

(def tz (t/default-time-zone))

(def date-formatter (tf/formatter tz "YYYY-MM-dd" "YYYY/MM/dd" "YYYYMMdd" "dd.MM.YYYY"))

(defn usage [options-summary]
  (str/join "\n"
            [""
             "Syncs Go.cd build history with buildviz"
             ""
             "Usage: buildviz.go.sync [OPTIONS] GO_URL"
             ""
             "GO_URL            The URL of the Go.cd installation"
             ""
             "Options"
             options-summary]))

(def cli-options
  [["-b" "--buildviz URL" "URL pointing to a running buildviz instance"
    :id :buildviz-url
    :default "http://localhost:3000"]
   ["-f" "--from DATE" "Date from which on builds are loaded, if not specified tries to pick up where the last run finished"
    :id :sync-start-time
    :parse-fn #(tf/parse date-formatter %)]
   ["-j" "--sync-jobs PIPELINE" "Pipelines for which Go jobs will be synced individually and not aggregated inside stage"
    :id :sync-jobs-for-pipelines
    :default nil
    :assoc-fn (fn [previous key val] (assoc previous key (conj (get previous key) val)))]
   ["-g" "--pipeline-group PIPELINE_GROUP" "Go pipeline groups to be synced, all by default"
    :id :pipeline-groups
    :default nil
    :assoc-fn (fn [previous key val] (assoc previous key (conj (get previous key) val)))]
   ["-h" "--help"]])


(def two-months-ago (t/minus (.withTimeAtStartOfDay (l/local-now)) (t/months 2)))

(defn- get-latest-synced-build-start [buildviz-url]
  (let [response (client/get (format "%s/status" (url/with-plain-text-password buildviz-url)))
        buildviz-status (j/parse-string (:body response) true)]
    (when-let [latest-build-start (:latestBuildStart buildviz-status)]
      (tc/from-long latest-build-start))))

(defn- get-start-date [buildviz-url date-from-config]
  (if (some? date-from-config)
    date-from-config
    (if-let [latest-sync-build-start (get-latest-synced-build-start buildviz-url)]
      latest-sync-build-start
      two-months-ago)))

(defn -main [& c-args]
  (let [args (parse-opts c-args cli-options)]
    (when (or (:help (:options args))
              (empty? (:arguments args)))
      (println (usage (:summary args)))
      (System/exit 0))
    (when (:errors args)
      (println (str/join "\n" (:errors args)))
      (System/exit 1))

    (let [go-url (url/url (first (:arguments args)))
          buildviz-url (url/url (:buildviz-url (:options args)))
          sync-start-time (get-start-date buildviz-url (:sync-start-time (:options args)))
          sync-jobs-for-pipelines (set (:sync-jobs-for-pipelines (:options args)))
          selected-pipeline-group-names (set (:pipeline-groups (:options args)))]

      (sync-jobs/sync-stages go-url buildviz-url sync-start-time sync-jobs-for-pipelines selected-pipeline-group-names))))
