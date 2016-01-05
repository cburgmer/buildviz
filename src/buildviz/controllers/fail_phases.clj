(ns buildviz.controllers.fail-phases
  (:require [buildviz.data.results :as results]
            [buildviz.pipelineinfo :as pipelineinfo]
            [buildviz.util
             [csv :as csv]
             [http :as http]]
            [clojure.string :as str]))

(defn- all-builds-in-order [build-results from-timestamp]
  (sort-by :end (results/all-builds build-results from-timestamp)))

(defn get-fail-phases [build-results accept from-timestamp]
  (let [fail-phases (pipelineinfo/pipeline-fail-phases (all-builds-in-order build-results from-timestamp))]
    (if (= (:mime accept) :json)
      (http/respond-with-json fail-phases)
      (http/respond-with-csv
       (csv/export-table ["start" "end" "culprits"]
                         (map (fn [{start :start end :end culprits :culprits}]
                                [(csv/format-timestamp start)
                                 (csv/format-timestamp end)
                                 (str/join "|" culprits)])
                              fail-phases))))))
