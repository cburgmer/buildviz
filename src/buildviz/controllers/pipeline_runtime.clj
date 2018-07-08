(ns buildviz.controllers.pipeline-runtime
  (:require [buildviz.analyse.pipelines :refer [pipelines]]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]
            [buildviz.util.csv :as csv]
            [clojure.string :as str]))

(defn- runtimes-as-list [runtime-by-day]
  (sort-by :date (map (fn [[date runtime]]
                        {:date date
                         :runtime runtime})
                      runtime-by-day)))

(defn get-pipeline-runtime [build-results accept from]
  (let [pipeline-list (pipelines (results/all-builds build-results from))]
    (if (= (:mime accept) :json)
      (http/respond-with-json pipeline-list)
      (http/respond-with-csv
       (csv/export-table ["pipeline" "start" "end"]
                         (->> pipeline-list
                              (map (fn [{:keys [pipeline start end]}]
                                     [(str/join "|" pipeline)
                                      (csv/format-timestamp start)
                                      (csv/format-timestamp end)]))))))))
