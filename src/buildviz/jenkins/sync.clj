(ns buildviz.jenkins.sync
  (:require [buildviz.jenkins.api :as api]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

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

    (->> (api/get-jobs jenkins-url)
         (mapcat (partial api/get-builds jenkins-url))
         (map jenkins-build->buildviz-build)
         (map (partial put-to-buildviz buildviz-url))
         doall)))
