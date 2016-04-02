(ns buildviz.teamcity.transform
  (:require [clj-time
             [coerce :as tc]
             [format :as tf]]))

(defn- date-str->timestamp [date-str]
  (->> date-str
       (tf/parse (tf/formatters :basic-date-time-no-ms))
       tc/to-epoch
       (* 1000)))

(defn- convert-test-results [{:keys [status startDate finishDate]}]
  {:outcome (if (= status "SUCCESS")
             "pass"
             "fail")
   :start (date-str->timestamp startDate)
   :end (date-str->timestamp finishDate)})

(defn teamcity-build->buildviz-build [{:keys [job-id build]}]
  {:job-name job-id
   :build-id (:number build)
   :build (convert-test-results build)})
