(ns buildviz.teamcity.sync
  (:gen-class)
  (:require [buildviz.teamcity.sync-jobs :as sync-jobs]
            [buildviz.util.url :as url]
            [clj-time
             [core :as t]
             [format :as tf]
             [local :as l]]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]))

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


(def two-months-ago (t/minus (.withTimeAtStartOfDay (l/local-now)) (t/months 2)))

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

      (sync-jobs/sync-jobs teamcity-url buildviz-url projects two-months-ago user-sync-start))))
