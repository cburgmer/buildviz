(ns buildviz.teamcity.sync
  (:gen-class)
  (:require [buildviz.teamcity
             [api :as api]
             [transform :as transform]]
            [buildviz.util.json :as json]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-progress.core :as progress]
            [clojure.string :as string]
            [clojure.tools
             [cli :refer [parse-opts]]
             [logging :as log]]))

(def cli-options
  [["-b" "--buildviz URL" "URL pointing to a running buildviz instance"
    :id :buildviz-url
    :default "http://localhost:3000"]
   ["-p" "--project PROJECT" "TeamCity project to be synced"
    :id :projects
    :default []
    :assoc-fn (fn [previous key val] (assoc previous key (conj (get previous key) val)))]
   ["-h" "--help"]])

(defn usage [options-summary]
  (string/join "\n"
               [""
                "Syncs TeamCity build history with buildviz"
                ""
                "Usage: buildviz.teamcity.sync [OPTIONS] TEAMCITY_URL"
                ""
                "TEAMCITY_URL           The URL of the TeamCity installation."
                ""
                "Options"
                options-summary]))


(defn- all-builds-for-job [teamcity-url job-id]
  (map (fn [build]
         {:build build
          :job-id job-id})
       (api/get-builds teamcity-url job-id)))

(defn add-test-results [teamcity-url build]
  (assoc build :tests (api/get-test-report teamcity-url (:id (:build build)))))

(defn- put-build [buildviz-url job-name build-id build]
  (client/put (string/join [buildviz-url (format "/builds/%s/%s" job-name build-id)])
              {:content-type :json
               :body (json/to-string build)}))

(defn put-test-results [buildviz-url job-name build-id test-results]
  (client/put (string/join [buildviz-url (format "/builds/%s/%s/testresults" job-name build-id)])
              {:content-type :json
               :body (j/generate-string test-results)}))

(defn- put-to-buildviz [buildviz-url {:keys [job-name build-id build test-results]}]
  (log/info (format "Syncing %s %s: build" job-name build-id))
  (put-build buildviz-url job-name build-id build)
  (when test-results
    (put-test-results buildviz-url job-name build-id test-results)))

(defn- sync-jobs [teamcity-url buildviz-url projects]
  (println "TeamCity" teamcity-url projects "-> buildviz" buildviz-url)
  (->> projects
       (mapcat #(api/get-jobs teamcity-url %))
       (mapcat #(all-builds-for-job teamcity-url %))
       (progress/init "Syncing")
       (map (partial add-test-results teamcity-url))
       (map transform/teamcity-build->buildviz-build)
       (map (partial put-to-buildviz buildviz-url))
       (map progress/tick)
       dorun
       (progress/done)))

(defn -main [& c-args]
  (let [args (parse-opts c-args cli-options)]
    (when (or (:help (:options args))
              (empty? (:arguments args)))
      (println (usage (:summary args)))
      (System/exit 0))

    (let [teamcity-url (first (:arguments args))
          buildviz-url (:buildviz-url (:options args))
          projects (:projects (:options args))]

      (sync-jobs teamcity-url buildviz-url projects))))
