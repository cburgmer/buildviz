(ns buildviz.http
  (:require [clojure.tools.logging :as log]))


(defn respond-with-json [content]
  {:body content})

(defn respond-with-csv [content]
  {:body content
   :headers {"Content-Type" "text/csv;charset=UTF-8"}})

(defn respond-with-xml [content]
  {:body content
   :headers {"Content-Type" "application/xml;charset=UTF-8"}})


(defn wrap-log-request [handler]
  (fn [req]
    (let [resp (handler req)
          method (.toUpperCase (name (:request-method req)))
          uri (:uri req)
          status (:status resp)]
      (log/info (format "\"%s %s\" %s" method uri status))
      resp)))

(defn wrap-log-errors [handler]
  (fn [req]
    (let [resp (handler req)
          status (:status resp)
          body (:body resp)
          method (.toUpperCase (name (:request-method req)))
          uri (:uri req)]
      (when (and (some? status) (>= status 400))
        (log/warn (format "Returned %s for %s %s: \"%s\"" status method uri body)))
      resp)))
