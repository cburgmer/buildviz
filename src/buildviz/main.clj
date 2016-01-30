(ns buildviz.main
  (:require [buildviz.data.results :as results]
            [buildviz
             [handler :as handler]
             [storage :as storage]]
            [buildviz.util.http :as http]
            [clojure.string :as str]))

(def ^:private data-dir (if-let [data-dir (System/getenv "BUILDVIZ_DATA_DIR")]
                data-dir
                "data/"))

(def ^:private pipeline-name (System/getenv "BUILDVIZ_PIPELINE_NAME"))

(def ^:private config-params [["BUILDVIZ_DATA_DIR" data-dir]
                              ["BUILDVIZ_PIPELINE_NAME" (or pipeline-name
                                                            "")]
                              ["PORT" (or (System/getenv "PORT")
                                          3000)]])

(defn help []
  (println "Starting buildviz with config")
  (->> config-params
       (map (fn [[env-var value]]
              (format "  %s: '%s'" env-var value)))
       (map println)
       doall))

(def app
  (let [builds (storage/load-builds data-dir)]
    (-> (results/build-results builds
                               (partial storage/load-testresults data-dir)
                               (partial storage/store-build! data-dir)
                               (partial storage/store-testresults! data-dir))
        (handler/create-app pipeline-name)
        http/wrap-log-request
        http/wrap-log-errors)))
