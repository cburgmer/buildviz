(ns buildviz.controllers.wait-times
  (:require [buildviz.analyse.wait-times :refer [wait-times]]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]
            [buildviz.util.csv :as csv]))

(defn get-wait-times [build-results accept from]
  (let [wait-times-results (wait-times (results/all-builds build-results from))]
    (if (= (:mime accept) :json)
      (http/respond-with-json wait-times-results)
      (http/respond-with-csv
       (csv/export-table ["job" "buildId" "start" "waitTime" "triggeredBy"]
                         (->> wait-times-results
                              (sort-by :start)
                              (map (fn [{:keys [job build-id start wait-time triggered-by]}]
                                     [job
                                      build-id
                                      (csv/format-timestamp start)
                                      (csv/format-duration wait-time)
                                      (format "%s/%s" (:job triggered-by) (:build-id triggered-by))]))))))))
