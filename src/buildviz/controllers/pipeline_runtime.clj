(ns buildviz.controllers.pipeline-runtime
  (:require [buildviz.analyse.pipelines :refer [pipeline-runtimes-by-day]]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]))

(defn- runtimes-as-list [runtime-by-day]
  (map (fn [[date runtime]]
         {:date date
          :runtime runtime})
       runtime-by-day))

(defn get-pipeline-runtime [build-results]
  (http/respond-with-json (->> (results/all-builds build-results 0)
                               pipeline-runtimes-by-day
                               (map (fn [[job-names runtime-by-day]]
                                      {:pipeline job-names
                                       :runtimes (runtimes-as-list runtime-by-day)})))))
