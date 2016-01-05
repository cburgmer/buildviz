(ns buildviz.main
  (:require [buildviz
             [handler :as handler]
             [storage :as storage]]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]))

(def data-dir (if-let [data-dir (System/getenv "BUILDVIZ_DATA_DIR")]
                data-dir
                "data"))

(def pipeline-name (System/getenv "BUILDVIZ_PIPELINE_NAME"))

(def app
  (let [builds (storage/load-builds data-dir)]
    (-> (results/build-results builds
                               (partial storage/load-testresults data-dir)
                               (partial storage/store-build! data-dir)
                               (partial storage/store-testresults! data-dir))
        (handler/create-app pipeline-name)
        http/wrap-log-request
        http/wrap-log-errors)))
