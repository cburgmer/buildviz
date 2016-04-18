(ns buildviz.controllers.status
  (:require [buildviz.data.results :as results]
            [buildviz.util.http :as http]
            [clj-time
             [coerce :as tc]
             [core :as t]]))

;; We manually calculate this period to be in sync with the JS implementation
(def ^:private two-months (* 2 30 24 60 60 1000))

(defn- recent-jobs [all-builds]
  (let [two-months-ago (- (tc/to-long (t/now)) two-months)]
    (filter #(< two-months-ago (:start %))
            all-builds)))

(defn- build-starts [all-builds]
  (->> all-builds
       (map :start)
       (remove nil?)))

(defn get-status [build-results pipeline-name]
  (let [all-builds (results/all-builds build-results)
        total-build-count (count all-builds)
        build-starts (seq (build-starts all-builds))
        recent-job-names (->> (recent-jobs all-builds)
                              (map :job)
                              distinct
                              seq)]
    (http/respond-with-json (cond-> {:totalBuildCount total-build-count}
                              pipeline-name (assoc :pipelineName pipeline-name)
                              build-starts (assoc :latestBuildStart (apply max build-starts)
                                                  :earliestBuildStart (apply min build-starts))
                              recent-job-names (assoc :recentJobNames recent-job-names)))))
