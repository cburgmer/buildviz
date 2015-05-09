(ns buildviz.handler
  (:use compojure.core
        ring.middleware.json)
  (:require [compojure.handler :as handler]))

(def builds (atom {}))

(defn- job-entry [job]
  (if (contains? @builds job)
    (@builds job)
    {}))

(defn- store-build! [job build buildData]
  (let [entry (job-entry job)
        updatedEntry (assoc entry build buildData)]
    (swap! builds assoc job updatedEntry)
    {:body buildData}))

(defn- get-build [job build]
  (if (contains? @builds job)
    (if-let [buildData ((@builds job) build)]
      {:body buildData}
      {:status 404})
    {:status 404}))

(defroutes app-routes
  (PUT "/builds/:job/:build" [job build :as {body :body}] (store-build! job build body))
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
