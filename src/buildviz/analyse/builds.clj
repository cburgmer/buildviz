(ns buildviz.analyse.builds
  (:require [buildviz.data.build-schema :refer [build-with-outcome? failed-build?]]
            [buildviz.util.math :as math]))

(defn builds-with-outcome [builds]
  (filter build-with-outcome? builds))


(defn builds-grouped-by-same-inputs [builds]
  (->> builds
       (filter :inputs)
       (group-by #(set (:inputs %)))
       vals))

;; flaky builds

(defn- outcomes-for-builds-grouped-by-input [builds]
  (->> builds
       builds-grouped-by-same-inputs
       (map #(map :outcome %))
       (map distinct)))

(defn flaky-build-count [builds]
  (->> (outcomes-for-builds-grouped-by-input builds)
       (map count)
       (filter #(< 1 %))
       count))

;; avg runtime

(defn- runtime-for [{:keys [start end]}]
  (- end start))

(defn- build-runtimes [builds]
  (map runtime-for (filter :end builds)))

(defn average-runtime [builds]
  (when-let [runtimes (seq (build-runtimes builds))]
    (math/avg runtimes)))


;; error count

(defn fail-count [builds]
  (count (filter failed-build? builds)))
