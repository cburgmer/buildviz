(ns buildviz.http
  (:require [clojure.tools.logging :as log]))

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
          uri (:uri req)]
      (when (and (some? status) (>= status 400))
        (log/warn (format "Returned %s for %s: \"%s\"" status uri body)))
      resp)))
