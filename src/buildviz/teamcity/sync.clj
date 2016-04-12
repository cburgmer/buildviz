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
                "TEAMCITY_URL           The URL of the TeamCity installation. You will most probably"
                "                       need some form of credentials. If 'guest user login' is"
                "                       enabled, you can try e.g. 'http://guest@localhost:8111'."
                ""
                "Options"
                options-summary]))


(defn- all-builds-for-job [teamcity-url {:keys [id projectName name]}]
  (map (fn [build]
         {:build build
          :project-name projectName
          :job-name name})
       (api/get-builds teamcity-url id)))

(defn add-test-results [teamcity-url build]
  (assoc build :tests (lazy-seq (api/get-test-report teamcity-url (:id (:build build))))))

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

(defn sync-jobs [teamcity-url buildviz-url projects]
  (println "TeamCity" (str teamcity-url) projects "-> buildviz" (str buildviz-url))
  (->> projects
       (mapcat #(api/get-jobs teamcity-url %))
       (mapcat #(all-builds-for-job teamcity-url %))
       (progress/init "Syncing")
       sync-oldest-first-to-deal-with-cancellation
       (map (partial add-test-results teamcity-url))
       (map transform/teamcity-build->buildviz-build)
       (map (partial put-to-buildviz buildviz-url))
       (map progress/tick)
       dorun
       (progress/done)))

(defn- assert-parameter [assert-func msg]
  (when (not (assert-func))
    (println msg)
    (System/exit 1)))

(defn -main [& c-args]
  (let [args (parse-opts c-args cli-options)]
    (when (:help (:options args))
      (println (usage (:summary args)))
      (System/exit 0))

    (let [teamcity-url (url/url (first (:arguments args)))
          buildviz-url (url/url (:buildviz-url (:options args)))
          projects (:projects (:options args))]

      (assert-parameter #(some? teamcity-url) "The URL of TeamCity is required. Try --help.")
      (assert-parameter #(not (empty? projects)) "At least one project is required. Try --help.")

      (sync-jobs teamcity-url buildviz-url projects))))
