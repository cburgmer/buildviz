(ns buildviz.controllers.builds
  (:require [buildviz.data
             [build-schema :as schema]
             [junit-xml :as junit-xml]
             [results :as results]
             [tests-schema :as tests-schema]
             [build-facts-schema :as build-facts-schema]]
            [buildviz.util
             [csv :as csv]
             [http :as http]
             [json :as json]]
            [cheshire.core :as j]
            [clojure.walk :as walk]
            [wharf.core :as wharf]))

(defn store-build! [build-results job-name build-id body]
  (let [build (json/from-string (slurp body))]
    (if-some [errors (seq (schema/build-validation-errors build))]
      {:status 400
       :body errors}
      (try
        (results/set-build! build-results job-name build-id build)
        (http/respond-with-json build)
        (catch IllegalArgumentException e
          {:status 400
           :body (.getMessage e)})))))

(defn get-build [build-results job-name build-id]
  (if-some [build (results/build build-results job-name build-id)]
    (http/respond-with-json build)
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

(defn- parse-json-test-results [body]
  (let [results (json/from-string (slurp body))]
    (if-some [errors (seq (tests-schema/tests-validation-errors results))]
      {:errors errors}
      {:test-results (junit-xml/serialize-testsuites results)})))

(defn- parse-test-results [body content-type]
  (if (= "application/json" content-type)
    (parse-json-test-results body)
    (parse-xml-test-results body)))

(defn store-test-results! [build-results job-name build-id body content-type]
  (let [{errors :errors test-results :test-results} (parse-test-results body content-type)]
    (if errors
      {:status 400
       :body errors}
      (try
        (results/set-tests! build-results job-name build-id test-results)
        {:status 204}
        (catch IllegalArgumentException e
          {:status 400
           :body (.getMessage e)})))))

(defn get-test-results [build-results job-name build-id accept]
  (try
    (if-some [content (results/tests build-results job-name build-id)]
      (if (= (:mime accept) :json)
        (http/respond-with-json (junit-xml/parse-testsuites content))
        (http/respond-with-xml content))
      {:status 404})
    (catch IllegalArgumentException e
      {:status 400
       :body (.getMessage e)})))

(defn get-builds [build-results accept from-timestamp]
  (let [builds (->> (results/all-builds build-results from-timestamp)
                    (map #(select-keys % [:job :build-id :start :end :outcome]))
                    (sort-by :start))]
    (if (= (:mime accept) :json)
      (http/respond-with-json builds)
      (http/respond-with-csv
       (csv/export-table ["job" "buildId" "start" "end" "outcome"]
                         (->> builds
                              (map (fn [{start :start end :end outcome :outcome job :job build-id :build-id}]
                                     [job
                                      build-id
                                      (csv/format-timestamp start)
                                      (csv/format-timestamp end)
                                      outcome]))))))))

(defn- store-build-and-test-results! [build-results {:keys [job-name build-id test-results] :as build}]
  (results/set-build! build-results job-name build-id (dissoc build :job-name :build-id :test-results))
  (when test-results
    (results/set-tests! build-results job-name build-id (junit-xml/serialize-testsuites test-results))))

(defn- ingest-builds [body]
  (let [builds-json (j/parsed-seq (clojure.java.io/reader body))
        first-error (->> builds-json
                         (map (fn [build] {:build build
                                           :errors (build-facts-schema/validation-errors build)}))
                         (remove (fn [build-errors] (empty? (:errors build-errors))))
                         first)]
    [(json/clojurize builds-json)
     first-error]))

(defn store-builds! [build-results body]
  (let [[builds first-error] (ingest-builds body)]
    (if first-error
      {:body (j/generate-string (json/jsonize first-error))
       :headers {"Content-Type" "application/json;charset=UTF-8"}
       :status 400}
      (do (run! #(store-build-and-test-results! build-results %) builds)
         {:status 204}))))
