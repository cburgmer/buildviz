(ns buildviz.jobinfo)

;; flaky builds

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

;; avg runtime

(defn- avg [series]
  (/ (reduce + series) (count series)))

(defn- duration-for [build]
  (if (and (contains? build :end) (contains? build :end))
    (- (build :end) (build :start))))

(defn- build-durations [build-data-entries]
  (filter (complement nil?)
          (map duration-for build-data-entries)))

(defn average-runtime [build-data-entries]
  (if-let [runtimes (seq (build-durations build-data-entries))]
    (avg runtimes)))
