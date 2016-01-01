(ns buildviz.jenkins.sync
  (:require [buildviz.jenkins.api :as api]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clj-progress.core :as progress]
            [clojure.tools.logging :as log]))

(defn add-test-results [jenkins-url {:keys [job-name number] :as build}]
  (assoc build :test-report (api/get-test-report jenkins-url job-name number)))

(defn- jenkins-test-case->buildviz-test-case [{:keys [:className :name :duration :status]}]
  {:classname className
   :name name
   :runtime (Math/round (* duration 1000))
   :status (case status
             "PASSED" "pass"
             "FAILED" "fail"
             "ERROR" "error"
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


(defn -main [& c-args]

  (let [jenkins-url "http://localhost:8080"
        buildviz-url "http://localhost:3000"]

    (->> (api/get-jobs jenkins-url)
         (mapcat (partial api/get-builds jenkins-url))
         (progress/init "Syncing")
         (map (partial add-test-results jenkins-url))
         (map jenkins-build->buildviz-build)
         (map (partial put-to-buildviz buildviz-url))
         (map progress/tick)
         dorun
         (progress/done))))
