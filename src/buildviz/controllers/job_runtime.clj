(ns buildviz.controllers.job-runtime
  (:require [buildviz.analyse.builds :refer [job-runtimes-by-day]]
            [buildviz.controllers.util :as util]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]))

(defn get-job-runtime [build-results from-timestamp]
  (let [runtimes-by-day (job-runtimes-by-day (results/all-builds build-results from-timestamp))]

    (http/respond-with-csv (util/durations-as-table runtimes-by-day))))
