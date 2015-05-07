(ns buildviz.handler
  (:use compojure.core
        ring.middleware.json)
  (:require [compojure.handler :as handler]))

(defn- store-build [req]
  (let [body (:body req)]
    {:body body}))

(defroutes app-routes
  (PUT "/builds/:job/:build" [job build] store-build))

(defn- wrap-log-request [handler]
  (fn [req]
    (println (:request-method req) (:uri req))
    (handler req)))

(def app
  (-> app-routes
      wrap-log-request
      wrap-json-response
      (wrap-json-body {:keywords? true})))
