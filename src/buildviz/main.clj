(ns buildviz.main
  (:require [buildviz.build-results :as results]
            [buildviz.handler :as handler]
            [buildviz.storage :as storage]
            [clojure.tools.logging :as log]))

(def jobs-filename "buildviz_jobs")


(defn- wrap-log-request [handler]
  (fn [req]
    (let [resp (handler req)
          method (.toUpperCase (name (:request-method req)))
          uri (:uri req)
          status (:status resp)]
      (log/info (format "\"%s %s\" %s" method uri status))
      resp)))

(defn- wrap-log-errors [handler]
  (fn [req]
    (let [resp (handler req)
          status (:status resp)
          body (:body resp)
          uri (:uri req)]
      (when (>= status 400)
        (log/warn (format "Returned %s for %s: \"%s\"" status uri body)))
      resp)))

(defn- persist-jobs! [build-data]
  (storage/store-jobs! build-data jobs-filename))

(def app
  (let [builds (atom (storage/load-jobs jobs-filename))] ; TODO hide atom inside record
    (-> (handler/create-app (results/build-results builds) persist-jobs!)
        wrap-log-request
        wrap-log-errors)))
