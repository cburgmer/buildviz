(ns buildviz.data.results
  (:require [clj-time.core :as t]
            [closchema.core :as schema]))

(defprotocol BuildResultsProtocol
  (last-modified [this])

  (job-names     [this])
  (builds        [this job-name]
                 [this job-name from])
  (all-builds    [this])
  (build         [this job-name build-id])
  (set-build!    [this job-name build-id build])

  (chronological-tests [this job-name from])
  (tests         [this job-name build-id])
  (set-tests!    [this job-name build-id xml]))


(defn- builds-starting-from [from builds]
  (let [from-timestamp (or from
                           0)]
    (filter (fn [[build-id {start :start}]]
              (<= from-timestamp start))
            builds)))

(defn- update-last-modified [build-results]
  (swap! (:last-modified-date build-results) (fn [_] (t/now))))

(defrecord BuildResults [last-modified-date builds load-tests store-build! store-tests!]
  BuildResultsProtocol

  (last-modified [_]
    @last-modified-date)

  (job-names [_]
    (keys @builds))

  (builds [this job-name]
    (->> job-name
         (get @builds)
         vals))

  (builds [this job-name from]
    (->> job-name
         (get @builds)
         (builds-starting-from from)
         vals))

  (all-builds [this]
    (->> @builds
         vals
         (mapcat vals)))

  (build [_ job-name build-id]
    (get-in @builds [job-name build-id]))

  (set-build! [this job-name build-id build-data]
    (store-build! job-name build-id build-data)
    (swap! builds assoc-in [job-name build-id] build-data)
    (update-last-modified this))

  ;; TODO find a solution for 'stale' tests with no matching builds
  (chronological-tests [this job-name from]
    (->> job-name
         (get @builds)
         (builds-starting-from from)
         keys
         (map #(load-tests job-name %))
         (remove nil?)
         seq))

  (tests [_ job-name build-id]
    (load-tests job-name build-id))

  (set-tests! [this job-name build-id xml]
    (store-tests! job-name build-id xml)
    (update-last-modified this)))

(defn build-results
  ([builds load-tests store-build! store-tests!]
   (build-results (t/now) builds load-tests store-build! store-tests!))

  ([last-modified builds load-tests store-build! store-tests!]
   (BuildResults. (atom last-modified)
                  (atom builds)
                  load-tests
                  store-build!
                  store-tests!)))
