(ns buildviz.teamcity.transform
  (:require [clj-time
             [coerce :as tc]
             [format :as tf]]
            [buildviz.teamcity.transform-tests :refer [convert-test-results]]))

(defn- full-job-name [project-name job-name]
  (format "%s %s" project-name job-name))


(defn parse-build-date [date-str]
  (tf/parse (tf/formatters :basic-date-time-no-ms)
            date-str))

(defn- date-str->timestamp [date-str]
  (->> (parse-build-date date-str)
       tc/to-epoch
       (* 1000)))

(defn- vcs-inputs [{revision :revision}]
  (map (fn [{version :version
             {name :name} :vcs-root-instance}]
         {:revision version
          :source-id name})
       revision))

(defn- triggered-by-snapshot-deps [{build :build} {type :type}]
  (when (= type "unknown")
    (map (fn [{number :number {name :name projectName :projectName} :buildType}]
           {:job-name (full-job-name projectName name)
            :build-id number})
         build)))

(defn- convert-build [{:keys [status startDate finishDate revisions
                              snapshot-dependencies triggered]}]
  (let [inputs (seq (vcs-inputs revisions))
        triggered-by (seq (triggered-by-snapshot-deps snapshot-dependencies
                                                      triggered))]
    (cond-> {:outcome (if (= status "SUCCESS")
                        "pass"
                        "fail")
             :start (date-str->timestamp startDate)
             :end (date-str->timestamp finishDate)}
      inputs (assoc :inputs inputs)
      triggered-by (assoc :triggered-by triggered-by))))


(defn teamcity-build->buildviz-build [{:keys [build tests project-name job-name]}]
  {:job-name (full-job-name project-name job-name)
   :build-id (:number build)
   :build (convert-build build)
   :test-results (convert-test-results tests)})
