(ns buildviz.controllers.wait-times
  (:require [buildviz.analyse.wait-times :refer [wait-times-by-day]]
            [buildviz.controllers.util :as util]
            [buildviz.data.results :as results]
            [buildviz.util.http :as http]))

(defn- wait-times-as-list [wait-times-by-day]
  (sort-by :date (map (fn [[date wait-time]]
                        {:date date
                         :wait-time wait-time})
                      wait-times-by-day)))

(defn get-wait-times [build-results accept from]
  (let [wait-times (wait-times-by-day (results/all-builds build-results from))]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> wait-times
                                   (map (fn [[job wait-times-by-day]]
                                          {:job job
                                           :wait-times (wait-times-as-list wait-times-by-day)}))))
      (http/respond-with-csv (util/durations-as-table wait-times)))))
