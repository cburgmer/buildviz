(ns buildviz.controllers.job-runtime
  (:require [buildviz.analyse.builds :refer [job-runtimes-by-day]]
            [buildviz.data.results :as results]
            [buildviz.util
             [csv :as csv]
             [http :as http]]))

(defn- remap-date-first [[job runtimes-by-day]]
  (map (fn [[day avg-runtime]]
         [day {job avg-runtime}])
       runtimes-by-day))

(defn- merge-runtimes [all-runtimes-by-day]
  (->> (mapcat remap-date-first all-runtimes-by-day)
       (group-by first)
       (map (fn [[date entries]]
              [date (apply merge (map second entries))]))))

(defn- runtime-table-entry [date runtimes job-names]
  (->> (map #(get runtimes %) job-names)
       (map csv/format-duration)
       (cons date)))

(defn- runtimes-as-table [job-names runtimes]
  (map (fn [[date runtimes-by-day]]
         (runtime-table-entry date runtimes-by-day job-names))
       runtimes))

(defn get-job-runtime [build-results from-timestamp]
  (let [runtimes-by-day (job-runtimes-by-day (results/all-builds build-results from-timestamp))
        job-names (keys runtimes-by-day)]

    (http/respond-with-csv (csv/export-table (cons "date" job-names)
                                             (->> (merge-runtimes runtimes-by-day)
                                                  (runtimes-as-table job-names)
                                                  (sort-by first))))))
