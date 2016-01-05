(ns buildviz.controllers.status
  (:require [buildviz.data.results :as results]
            [buildviz.util.http :as http]))

(defn- with-latest-build-start [all-builds response]
  (if-let [build-starts (seq (remove nil? (map :start all-builds)))]
    (assoc response
           :latestBuildStart (apply max build-starts)
           :earliestBuildStart (apply min build-starts))
    response))

(defn get-status [build-results pipeline-name]
  (let [all-builds (results/all-builds build-results)
        total-build-count (count all-builds)]
    (http/respond-with-json (with-latest-build-start all-builds
                              {:totalBuildCount total-build-count
                               :pipelineName pipeline-name}))))
