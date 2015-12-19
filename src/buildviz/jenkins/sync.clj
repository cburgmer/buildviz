(ns buildviz.jenkins.sync
  (:require [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-time
             [coerce :as tc]
             [format :as tf]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- get-jobs [jenkins-url]
  (let [response (j/parse-string (:body (client/get (string/join [jenkins-url "/api/json"])))
                                 true)]
    (map :name (get response :jobs))))

(defn- get-builds [jenkins-url job-name]
  (let [response (j/parse-string (:body (client/get (string/join [jenkins-url (format "/job/%s/api/json?pretty=true&tree=builds[number,timestamp,result,duration]" job-name)])))
                                 true)]
    (->> (get response :builds)
         (map #(assoc % :job-name job-name)))))


(defn- jenkins-build->buildviz-build [{:keys [job-name number timestamp duration result]}]
  {:job-name job-name
   :build-id number
   :build {:start timestamp
           :end (+ timestamp duration)
           :outcome (if (= result "SUCCESS")
                      "pass"
                      "fail")}})

(defn put-build [buildviz-url job-name build-id build]
  (client/put (string/join [buildviz-url (format "/builds/%s/%s" job-name build-id)])
              {:content-type :json
               :body (j/generate-string build)}))


(defn put-to-buildviz [buildviz-url {:keys [job-name build-id build]}]
  (log/info (format "Syncing %s %s: build" job-name build-id))
  (put-build buildviz-url job-name build-id build))


(defn -main [& c-args]

  (let [jenkins-url "http://localhost:8080"
        buildviz-url "http://localhost:3000"]

    (->> (get-jobs jenkins-url)
         (mapcat (partial get-builds jenkins-url))
         (map jenkins-build->buildviz-build)
         (map (partial put-to-buildviz buildviz-url))
         doall)))
