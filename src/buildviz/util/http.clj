(ns buildviz.util.http
  (:require [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [buildviz.util.json :as json]
            [ring.util
             [response :as resp]
             [time :as time]]
            [wharf.core :as wharf]))

(defn respond-with-json [content]
  {:body (json/to-string content)
   :headers {"Content-Type" "application/json;charset=UTF-8"}})

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


(defn- ^java.util.Date date-header [response header]
  (if-let [http-date (resp/get-header response header)]
    (time/parse-date http-date)))

(defn- lose-millis-precision [date]
  (t/minus date (t/millis (t/milli date))))

(defn- not-modified-since? [request modified-date]
  (let [modified-since (date-header request "if-modified-since")
        last-modified (.toDate (lose-millis-precision modified-date))]
    (and modified-since
         (not (.before modified-since last-modified)))))

(defn- resolve-handler-if-modified [req handler modified-date]
  (if (not-modified-since? req modified-date)
    (-> (resp/response nil)
        (resp/status 304))
    (handler req)))

(defn- ok-response? [response]
  (= (:status response) 200))

(defn- apply-last-modified [response last-modified]
  (if (ok-response? response)
    (-> response
        (resp/header "Last-Modified" (time/format-date (.toDate last-modified))))
    response))

(defn not-modified-request [handler last-modified request]
  (-> request
      (resolve-handler-if-modified handler last-modified)
      (apply-last-modified last-modified)))


(defn- extract-format [{uri :uri}]
  (when-let [match (re-matches #"(.+)\.(\w+)" uri)]
    {:base-uri (nth match 1)
     :format (keyword (nth match 2))}))

(defn- rewrite-resource-uri-with-format [request format-mime-map]
  (let [{base-uri :base-uri
         format :format} (extract-format request)]
    (if (and format
             (contains? format-mime-map format))
      (let [mime-type (get format-mime-map format)]
        (-> request
          (assoc :uri base-uri)
          (assoc-in [:headers "accept"] mime-type)))
      request)))

(defn wrap-resource-format [handler format-mime-map]
  (fn [request]
    (handler (rewrite-resource-uri-with-format request format-mime-map))))
