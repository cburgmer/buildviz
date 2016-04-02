(ns buildviz.teamcity.transform
  (:require [clj-time
             [coerce :as tc]
             [format :as tf]]))

(defn- date-str->timestamp [date-str]
  (->> date-str
       (tf/parse (tf/formatters :basic-date-time-no-ms))
       tc/to-epoch
       (* 1000)))

(defn- convert-build [{:keys [status startDate finishDate]}]
  {:outcome (if (= status "SUCCESS")
             "pass"
             "fail")
   :start (date-str->timestamp startDate)
   :end (date-str->timestamp finishDate)})


(defn- parse-teamcity-test-name [full-name]
  (let [match (re-matches #"^(?:(.+): )?(?:([^:]+)\.)?([^:\.]+)$" full-name)
        suites (nth match 1)
        classname (nth match 2)
        test-name (nth match 3)]
    (-> {:suite suites
         :name test-name
         :classname classname})))

(defn- convert-status [ignored status]
  (if ignored
    "skipped"
    (if (= status "SUCCESS")
      "pass"
      "fail")))

(defn- convert-test [{:keys [name status ignored duration]}]
  (-> (parse-teamcity-test-name name)
      (assoc :status (convert-status ignored status))
      (assoc :runtime (or duration 0)))) ; force duration, as buildviz currently can't handle a missing value

(defn- convert-test-results [tests]
  (->> tests
       (map convert-test)
       (group-by :suite)
       (map (fn [[suite tests]]
              {:name suite
               :children (map #(dissoc % :suite) tests)}))
       seq))


(defn teamcity-build->buildviz-build [{:keys [job-id build tests]}]
  {:job-name job-id
   :build-id (:number build)
   :build (convert-build build)
   :test-results (convert-test-results tests)})
