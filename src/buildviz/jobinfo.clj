(ns buildviz.jobinfo)

(defn builds-with-outcome [build-data-entries]
  (filter #(contains? % :outcome) build-data-entries))

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

(defn- runtime-for [build]
  (if (and (contains? build :start)
           (contains? build :end))
    (- (build :end) (build :start))))

(defn- build-runtime [build-data-entries]
  (filter (complement nil?)
          (map runtime-for build-data-entries)))

(defn average-runtime [build-data-entries]
  (if-let [runtimes (seq (build-runtime build-data-entries))]
    (avg runtimes)))

;; error count

(defn- failed-build? [build]
  (= "fail" (:outcome build)))

(defn fail-count [build-data-entries]
  (count (filter failed-build? build-data-entries)))
