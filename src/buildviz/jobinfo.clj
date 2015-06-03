(ns buildviz.jobinfo)

(defn- outcomes-for-builds-grouped-by-input [build-data-entries]
  (->> (filter #(contains? % :inputs) build-data-entries)
       (group-by :inputs)
       vals
       (map #(map :outcome %))
       (map distinct)))

(defn flaky-build-count [build-data-entries]
  (->> (outcomes-for-builds-grouped-by-input build-data-entries)
       (map count)
       (filter #(< 1 %))
       count))
