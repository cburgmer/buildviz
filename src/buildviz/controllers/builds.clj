(ns buildviz.controllers.builds
  (:require [buildviz
             [http :as http]
             [junit-xml :as junit-xml]]
            [buildviz.data
             [results :as results]
             [schema :as schema]]
            [clojure.walk :as walk]))

(defn store-build! [build-results job-name build-id build-data]
  (if-some [errors (seq (schema/build-data-validation-errors build-data))]
    {:status 400
     :body errors}
    (do (results/set-build! build-results job-name build-id build-data)
        (http/respond-with-json build-data))))

(defn get-build [build-results job-name build-id]
  (if-some [build-data (results/build build-results job-name build-id)]
    (http/respond-with-json build-data)
    {:status 404}))

(defn- force-evaluate-junit-xml [content]
  (walk/postwalk identity (junit-xml/parse-testsuites content)))

(defn store-test-results! [build-results job-name build-id body content-type]
  (if (= "application/json" content-type)
    (do
     (results/set-tests! build-results job-name build-id (junit-xml/serialize-testsuites body))
     {:status 204})
    (let [content (slurp body)]
      (try
        (force-evaluate-junit-xml content)
        (results/set-tests! build-results job-name build-id content)
        {:status 204}
        (catch Exception e
          {:status 400
           :body (.getMessage e)})))))

(defn get-test-results [build-results job-name build-id accept]
  (if-some [content (results/tests build-results job-name build-id)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (junit-xml/parse-testsuites content))
      (http/respond-with-xml content))
    {:status 404}))
