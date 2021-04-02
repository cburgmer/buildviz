(ns buildviz.test-utils
  (:require [buildviz.data.results :as results]
            [buildviz.handler :as handler]
            [cheshire.core :as json]
            [ring.mock.request :refer :all]))

(defn dummy-persist [_ _ _])

(defn- load-tests-from [mock-testresults]
  (fn [job-name build-id]
    (get-in @mock-testresults [job-name build-id])))

(defn the-app
  ([]
   (the-app {} {}))
  ([jobs testresults]
   (let [stored-testresults (if (instance? clojure.lang.Atom testresults)
                              testresults
                              (atom testresults))
         persist-testresults (fn [job-name build-id xml]
                               (swap! stored-testresults assoc-in [job-name build-id] xml))]
     (handler/create-app (results/build-results jobs
                                                (load-tests-from stored-testresults)
                                                dummy-persist
                                                persist-testresults)
                         "Test Pipeline"))))

;; helpers

(defn get-request
  ([app url]
   (get-request app url {}))
  ([app url query-params]
   (app (-> (request :get url)
            (query-string query-params)))))

(defn plain-get-request [app url]
  (app (-> (request :get url)
           (header :accept "text/plain"))))

(defn json-get-request
  ([app url]
   (json-get-request app url {}))
  ([app url query-params]
   (app (-> (request :get url)
            (query-string query-params)
            (header :accept "application/json")))))

(defn json-put-request [app url json]
  (app (-> (request :put url)
           (body (json/generate-string json))
           (content-type "application/json"))))

(defn xml-put-request [app url xml]
  (app (-> (request :put url)
           (body xml)
           (content-type "application/xml"))))

(defn post-request [app url the-body the-content-type]
  (app (-> (request :post url)
           (body the-body)
           (content-type the-content-type))))

(defn json-body [response]
  (json/parse-string (:body response)))
