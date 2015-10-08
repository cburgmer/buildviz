(ns buildviz.main
  (:require [buildviz.build-results :as results]
            [buildviz.handler :as handler]
            [buildviz.http :as http]
            [buildviz.storage :as storage]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def data-dir (if-let [data-dir (System/getenv "BUILDVIZ_DATA_DIR")]
                data-dir
                "data"))


(defn- persist-build! [job-name build-id build]
  (storage/store-build! job-name build-id build data-dir))

(defn- persist-testresults! [job-name build-id xml]
  (storage/store-testresults! job-name build-id xml data-dir))

(defn- load-testresults [job-name build-id]
  (storage/load-testresults job-name build-id data-dir))

(def app
  (let [builds (storage/load-builds data-dir)]
    (-> (handler/create-app (results/build-results builds
                                                   load-testresults
                                                   persist-build!
                                                   persist-testresults!))
        http/wrap-log-request
        http/wrap-log-errors)))
