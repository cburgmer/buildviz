(ns buildviz.jenkins.api
  (:require [cheshire.core :as j]
            [clj-http.client :as client]
            [clojure.string :as string]))

(defn- get-json [jenkins-url relative-url]
  (j/parse-string (:body (client/get (string/join [jenkins-url relative-url]))) true))

(defn get-jobs [jenkins-url]
  (let [response (get-json jenkins-url "/api/json")]
    (map :name (get response :jobs))))

(defn get-builds [jenkins-url job-name]
  (let [response (get-json jenkins-url (format "/job/%s/api/json?pretty=true&tree=builds[number,timestamp,duration,result]" job-name))]
    (->> (get response :builds)
         (map #(assoc % :job-name job-name)))))
