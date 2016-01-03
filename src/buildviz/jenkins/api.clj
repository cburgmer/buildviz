(ns buildviz.jenkins.api
  (:require [cheshire.core :as j]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- get-json [jenkins-url relative-url]
  (j/parse-string (:body (client/get (string/join [jenkins-url relative-url]))) true))

(defn get-jobs [jenkins-url]
  (let [response (get-json jenkins-url "/api/json")]
    (map :name (get response :jobs))))

(defn get-builds [jenkins-url job-name]
  (let [response (get-json jenkins-url (format "/job/%s/api/json?tree=builds[number,timestamp,duration,result,actions[lastBuiltRevision[SHA1],remoteUrls]]" job-name))]
    (->> (get response :builds)
         (map #(assoc % :job-name job-name)))))

(defn get-test-report [jenkins-url job-name build-number]
  (let [test-report-url (format "/job/%s/%s/testReport/api/json" job-name build-number)]
    (try
      (get-json jenkins-url test-report-url)
      (catch Exception e
        (if (or (not (ex-data e))
                (not= 404 (:status (ex-data e))))
          (log/errorf e "Unable to get test report from %s" test-report-url))))))
