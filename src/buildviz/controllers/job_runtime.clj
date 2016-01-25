(ns buildviz.controllers.job-runtime
  (:require [buildviz.analyse.builds :refer [job-runtimes-by-day]]
            [buildviz.controllers.util :as util]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]))

(defn- runtimes-as-list [runtime-by-day]
  (sort-by :date (map (fn [[date runtime]]
                        {:date date
                         :runtime runtime})
                      runtime-by-day)))

(defn get-job-runtime [build-results accept from-timestamp]
  (let [runtimes-by-day (job-runtimes-by-day (results/all-builds build-results from-timestamp))]

    (if (= (:mime accept) :json)
      (http/respond-with-json (->> runtimes-by-day
                                   (map (fn [[job runtime-by-day]]
                                          {:job job
                                           :runtimes (runtimes-as-list runtime-by-day)}))))
      (http/respond-with-csv (util/durations-as-table runtimes-by-day)))))
