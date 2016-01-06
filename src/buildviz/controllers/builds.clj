(ns buildviz.controllers.builds
  (:require [buildviz.data
             [build-schema :as schema]
             [results :as results]
             [tests-schema :as tests-schema]]
            [buildviz.junit-xml :as junit-xml]
            [buildviz.util.http :as http]
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

(defn- parse-xml-test-results [body]
  (let [content (slurp body)]
    (try
      (force-evaluate-junit-xml content)
      {:test-results content}
      (catch Exception e
        {:errors (.getMessage e)}))))

(defn- parse-test-results [body content-type]
  (if (= "application/json" content-type)
    (if-some [errors (seq (tests-schema/tests-validation-errors body))]
      {:errors errors}
      {:test-results (junit-xml/serialize-testsuites body)})
    (parse-xml-test-results body)))

(defn store-test-results! [build-results job-name build-id body content-type]
  (let [{errors :errors test-results :test-results} (parse-test-results body content-type)]
    (if errors
      {:status 400
       :body errors}
      (do
        (results/set-tests! build-results job-name build-id test-results)
        {:status 204}))))

(defn get-test-results [build-results job-name build-id accept]
  (if-some [content (results/tests build-results job-name build-id)]
    (if (= (:mime accept) :json)
      (http/respond-with-json (junit-xml/parse-testsuites content))
      (http/respond-with-xml content))
    {:status 404}))
