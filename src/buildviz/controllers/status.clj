(ns buildviz.controllers.status
  (:require [buildviz.data.results :as results]
            [buildviz.util.http :as http]))

(defn get-status [build-results pipeline-name]
  (let [all-builds (results/all-builds build-results)
        total-build-count (count all-builds)
        build-starts (->> all-builds
                          (map :start)
                          (remove nil?)
                          seq)]
    (http/respond-with-json (cond-> {:totalBuildCount total-build-count}
                              pipeline-name (assoc :pipelineName pipeline-name)
                              build-starts (assoc :latestBuildStart (apply max build-starts)
                                                  :earliestBuildStart (apply min build-starts))))))
