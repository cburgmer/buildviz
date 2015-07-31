(ns buildviz.main
  (:require [buildviz.build-results :as results]
            [buildviz.handler :as handler]
            [buildviz.http :as http]
            [buildviz.storage :as storage]))

(def jobs-filename "buildviz_jobs")
(def tests-filename "buildviz_tests")


(defn- persist-jobs! [build-data]
  (storage/store-jobs! build-data jobs-filename))

(defn- persist-tests! [tests-data]
  (storage/store-jobs! tests-data tests-filename))


(def app
  (let [builds (storage/load-jobs jobs-filename)
        tests (storage/load-jobs tests-filename)]
    (-> (handler/create-app (results/build-results builds tests)
                            persist-jobs!
                            persist-tests!)
        http/wrap-log-request
        http/wrap-log-errors)))
