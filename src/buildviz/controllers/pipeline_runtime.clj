(ns buildviz.controllers.pipeline-runtime
  (:require [buildviz.analyse.pipelines :refer [pipeline-runtimes-by-day]]
            [buildviz.data.results :as results]
            [buildviz.util.csv :as csv]
            [buildviz.util.http :as http]
            [clojure.string :as str]))

(defn- remap-date-first [[key runtimes-by-day]]
  (map (fn [[day avg-runtime]]
         [day {key avg-runtime}])
       runtimes-by-day))

(defn- merge-runtimes [all-runtimes-by-day]
  (->> (mapcat remap-date-first all-runtimes-by-day)
       (group-by first)
       (map (fn [[date entries]]
              [date (apply merge (map second entries))]))))

(defn- serialize-pipelines [pipelines]
  (map #(str/join "|" %) pipelines))

(defn- runtime-of-day-row [pipelines [date runtimes-by-pipeline]]
  (->> (map #(get runtimes-by-pipeline %) pipelines)
       (map csv/format-duration)
       (cons date)))

(defn- pipeline-runtime-as-table [pipeline-runtimes]
  (let [pipelines (keys pipeline-runtimes)]
    (csv/export-table (cons "date" (serialize-pipelines pipelines))
                      (->> (merge-runtimes pipeline-runtimes)
                           (sort-by first)
                           (map #(runtime-of-day-row pipelines %))))))

(defn- runtimes-as-list [runtime-by-day]
  (map (fn [[date runtime]]
         {:date date
          :runtime runtime})
       runtime-by-day))

(defn get-pipeline-runtime [build-results accept from]
  (let [pipeline-runtimes (pipeline-runtimes-by-day (results/all-builds build-results from))]
    (if (= (:mime accept) :json)
      (http/respond-with-json (->> pipeline-runtimes
                                   (map (fn [[job-names runtime-by-day]]
                                          {:pipeline job-names
                                           :runtimes (runtimes-as-list runtime-by-day)}))))
      (http/respond-with-csv (pipeline-runtime-as-table pipeline-runtimes)))))
