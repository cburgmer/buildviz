(ns buildviz.handler
  (:use compojure.core
        ring.middleware.json)
  (:require [compojure.handler :as handler]))

(def builds (atom {}))

(defn- store-build [job build buildData]
  (swap! builds assoc build buildData)
  {:body buildData})

(defn- get-build [job build]
  (let [buildData (@builds build)]
    {:body buildData}))

(defroutes app-routes
  (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build job build body))
  (GET "/builds/:job/:build" [job build] (get-build job build)))

(defn- wrap-log-request [handler]
  (fn [req]
    (println (:request-method req) (:uri req))
    (handler req)))

(def app
  (-> app-routes
      wrap-log-request
      wrap-json-response
      (wrap-json-body {:keywords? true})))
