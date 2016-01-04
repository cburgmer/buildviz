(ns buildviz.jenkins.sync
  (:require [buildviz.jenkins
             [api :as api]
             [transform :as transform]]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-progress.core :as progress]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [local :as l]]
            [clojure.string :as string]
            [clojure.tools
             [cli :refer [parse-opts]]
             [logging :as log]]))

(def cli-options
  [["-b" "--buildviz URL" "URL pointing to a running buildviz instance"
    :id :buildviz-url
    :default "http://localhost:3000"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (string/join "\n"
               [""
                "Syncs Jenkins build history with buildviz"
                ""
                "Usage: buildviz.jenkins.sync [OPTIONS] JENKINS_URL"
                ""
                "JENKINS_URL            The URL of the Jenkins installation"
                ""
                "Options"
                options-summary]))

(defn add-test-results [jenkins-url {:keys [job-name number] :as build}]
  (assoc build :test-report (api/get-test-report jenkins-url job-name number)))


(defn put-build [buildviz-url job-name build-id build]
  (client/put (string/join [buildviz-url (format "/builds/%s/%s" job-name build-id)])
              {:content-type :json
               :body (j/generate-string build)}))

(defn put-test-results [buildviz-url job-name build-id test-results]
  (client/put (string/join [buildviz-url (format "/builds/%s/%s/testresults" job-name build-id)])
              {:content-type :json
               :body (j/generate-string test-results)}))

(defn put-to-buildviz [buildviz-url {:keys [job-name build-id build test-results]}]
  (log/info (format "Syncing %s %s: build" job-name build-id))
  (put-build buildviz-url job-name build-id build)
  (when test-results
    (put-test-results buildviz-url job-name build-id test-results)))


(defn- jenkins-build->start-time [{timestamp :timestamp}]
  (tc/from-long timestamp))

(defn- all-builds-for-job [jenkins-url sync-start-time job-name]
  (let [safe-build-start-time (t/minus sync-start-time (t/millis 1))]
    (->> (api/get-builds jenkins-url job-name)
         (take-while #(t/after? (jenkins-build->start-time %) safe-build-start-time)))))

(defn- sync-jobs [jenkins-url buildviz-url sync-start-time]
  (->> (api/get-jobs jenkins-url)
       (mapcat #(all-builds-for-job jenkins-url sync-start-time %))
       (progress/init "Syncing")
       (map (partial add-test-results jenkins-url))
       (map transform/jenkins-build->buildviz-build)
       (map (partial put-to-buildviz buildviz-url))
       (map progress/tick)
       dorun
       (progress/done)))


(def last-week (t/minus (.withTimeAtStartOfDay (l/local-now)) (t/weeks 1)))

(defn- get-latest-synced-build-start [buildviz-url]
  (let [response (client/get (format "%s/status" buildviz-url))
        buildviz-status (j/parse-string (:body response) true)]
    (when-let [latest-build-start (:latestBuildStart buildviz-status)]
      (tc/from-long latest-build-start))))

(defn- get-start-date [buildviz-url]
  (if-let [latest-sync-build-start (get-latest-synced-build-start buildviz-url)]
    latest-sync-build-start
    last-week))


(defn -main [& c-args]
  (let [args (parse-opts c-args cli-options)]
    (when (or (:help (:options args))
              (empty? (:arguments args)))
      (println (usage (:summary args)))
      (System/exit 0))

    (let [jenkins-url (first (:arguments args))
          buildviz-url (:buildviz-url (:options args))
          sync-start-time (get-start-date buildviz-url)]

      (sync-jobs jenkins-url buildviz-url sync-start-time))))
