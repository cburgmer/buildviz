(ns buildviz.controllers.pipeline-runtime
  (:require [buildviz.analyse.pipelines :refer [pipeline-runtimes-by-day]]
            [buildviz.controllers.util :as util]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]
            [clojure.string :as str]))

(defn- runtimes-as-list [runtime-by-day]
  (sort-by :date (map (fn [[date runtime]]
                        {:date date
                         :runtime runtime})
                      runtime-by-day)))

(defn get-pipeline-runtime [build-results accept from]
  (let [pipeline-runtimes (pipeline-runtimes-by-day (results/all-builds build-results from))]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> pipeline-runtimes
                                   (map (fn [[job-names runtime-by-day]]
                                          {:pipeline job-names
                                           :runtimes (runtimes-as-list runtime-by-day)}))))
      (http/respond-with-csv (->> pipeline-runtimes
                                  (map (fn [[pipeline runtimes]]
                                         [(str/join "|" pipeline) runtimes]))
                                  (into {})
                                  (util/durations-as-table))))))
