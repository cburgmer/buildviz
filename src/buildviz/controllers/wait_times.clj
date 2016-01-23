(ns buildviz.controllers.wait-times
  (:require [buildviz.analyse.wait-times :refer [wait-times-by-day]]
            [buildviz.data.results :as results]
            [buildviz.util.csv :as csv]
            [buildviz.util.http :as http]))


(defn- remap-date-first [[key wait-times-by-day]]
  (map (fn [[day avg-wait-time]]
         [day {key avg-wait-time}])
       wait-times-by-day))

(defn- merge-wait-times [all-wait-times-by-day]
  (->> (mapcat remap-date-first all-wait-times-by-day)
       (group-by first)
       (map (fn [[date entries]]
              [date (apply merge (map second entries))]))))

(defn- wait-times-of-day-row [jobs [date wait-times-by-job]]
  (->> (map #(get wait-times-by-job %) jobs)
       (map csv/format-duration)
       (cons date)))

(defn- wait-times-as-table [wait-times]
  (let [jobs (keys wait-times)]
    (csv/export-table (cons "date" jobs)
                      (->> (merge-wait-times wait-times)
                           (sort-by first)
                           (map #(wait-times-of-day-row jobs %))))))

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
      (http/respond-with-csv (wait-times-as-table wait-times)))))
