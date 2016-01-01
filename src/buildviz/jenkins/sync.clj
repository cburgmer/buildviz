(ns buildviz.jenkins.sync
  (:require [buildviz.jenkins.api :as api]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clj-progress.core :as progress]
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

(defn- jenkins-test-case->buildviz-test-case [{:keys [:className :name :duration :status]}]
  {:classname className
   :name name
   :runtime (Math/round (* duration 1000))
   :status (case status
             "PASSED" "pass"
             "FIXED" "pass"
             "REGRESSION" "fail"
             "FAILED" "fail"
             "SKIPPED" "skipped")})

(defn- jenkins-suite->buildviz-suite [{:keys [:name :cases]}]
  {:name name
   :children (map jenkins-test-case->buildviz-test-case cases)})

(defn- convert-test-results [test-report]
  (when test-report
    (->> (get test-report :suites)
         (map jenkins-suite->buildviz-suite))))

(defn- jenkins-build->buildviz-build [{:keys [job-name number timestamp duration result test-report]}]
  {:job-name job-name
   :build-id number
   :build {:start timestamp
           :end (+ timestamp duration)
           :outcome (if (= result "SUCCESS")
                      "pass"
                      "fail")}
   :test-results (convert-test-results test-report)})

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


(defn- sync-jobs [jenkins-url buildviz-url]
  (->> (api/get-jobs jenkins-url)
       (mapcat (partial api/get-builds jenkins-url))
       (progress/init "Syncing")
       (map (partial add-test-results jenkins-url))
       (map jenkins-build->buildviz-build)
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

    (let [jenkins-url (first (:arguments args))
          buildviz-url (:buildviz-url (:options args))]

      (sync-jobs jenkins-url buildviz-url))))
