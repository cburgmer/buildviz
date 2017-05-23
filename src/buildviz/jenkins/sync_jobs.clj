(ns buildviz.jenkins.sync-jobs
  (:require [buildviz.jenkins
             [api :as api]
             [transform :as transform]]
            [buildviz.util.json :as json]
            [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.client :as client]
            [clj-progress.core :as progress]
            [clj-time
             [coerce :as tc]
             [core :as t]
             [format :as tf]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [uritemplate-clj.core :as templ]))

(defn add-test-results [jenkins-url {:keys [job-name number] :as build}]
  (assoc build :test-report (api/get-test-report jenkins-url job-name number)))


(defn- put-build [buildviz-url job-name build-id build]
  (client/put (string/join [(url/with-plain-text-password buildviz-url) (templ/uritemplate "/builds{/job}{/build}" {"job" job-name "build" build-id})])
              {:content-type :json
               :body (json/to-string build)}))

(defn- put-test-results [buildviz-url job-name build-id test-results]
  (client/put (string/join [(url/with-plain-text-password buildviz-url) (templ/uritemplate "/builds{/job}{/build}/testresults" {"job" job-name "build" build-id})])
              {:content-type :json
               :body (j/generate-string test-results)}))

(defn put-to-buildviz [buildviz-url {:keys [job-name build-id build test-results]}]
  (log/info (format "Syncing %s %s: build" job-name build-id))
  (put-build buildviz-url job-name build-id build)
  (when test-results
    (put-test-results buildviz-url job-name build-id test-results)))


(defn- jenkins-build->start-time [{timestamp :timestamp}]
  (tc/from-long timestamp))

(defn- ignore-ongoing-builds [builds]
  (filter :result builds))

(defn- all-builds-for-job [jenkins-url sync-start-time job-name]
  (let [safe-build-start-time (t/minus sync-start-time (t/millis 1))]
    (->> (api/get-builds jenkins-url job-name)
         (take-while #(t/after? (jenkins-build->start-time %) safe-build-start-time))
         ignore-ongoing-builds)))

(defn- sync-oldest-first-to-deal-with-cancellation [builds]
  (sort-by :timestamp builds))

(defn sync-jobs [jenkins-url buildviz-url sync-start-time]
  (println "Jenkins" (str jenkins-url) "-> buildviz" (str buildviz-url))
  (println (format "Finding all builds for syncing (starting from %s)..."
                 (tf/unparse (:date-time tf/formatters) sync-start-time)))
  (->> (api/get-jobs jenkins-url)
       (mapcat #(all-builds-for-job jenkins-url sync-start-time %))
       (progress/init "Syncing")
       sync-oldest-first-to-deal-with-cancellation
       (map (partial add-test-results jenkins-url))
       (map transform/jenkins-build->buildviz-build)
       (map (partial put-to-buildviz buildviz-url))
       (map progress/tick)
       dorun
       (progress/done)))
