(ns buildviz.main
  (:require [buildviz.build-results :as results]
            [buildviz.handler :as handler]
            [buildviz.http :as http]
            [buildviz.storage :as storage]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- path-for [file-name]
  (if-let [data-dir (System/getenv "BUILDVIZ_DATA_DIR")]
    (.getPath (io/file data-dir file-name))
    file-name))

(def jobs-filename (path-for "buildviz_jobs"))
(def tests-filename (path-for "buildviz_tests"))


(defn- persist-jobs! [build-data]
  (storage/store! build-data jobs-filename))

(defn- persist-tests! [tests-data]
  (storage/store! tests-data tests-filename))


(def app
  (let [builds (storage/load-from-file jobs-filename)
        tests (storage/load-from-file tests-filename)]
    (-> (handler/create-app (results/build-results builds tests)
                            persist-jobs!
                            persist-tests!)
        http/wrap-log-request
        http/wrap-log-errors)))
