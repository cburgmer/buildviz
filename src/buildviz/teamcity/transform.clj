(ns buildviz.teamcity.transform
  (:require [clj-time
             [coerce :as tc]
             [format :as tf]]))

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

(defn- convert-build [{:keys [status startDate finishDate revisions]}]
  (let [inputs (seq (vcs-inputs revisions))]
    (cond-> {:outcome (if (= status "SUCCESS")
                        "pass"
                        "fail")
             :start (date-str->timestamp startDate)
             :end (date-str->timestamp finishDate)}
      inputs (assoc :inputs inputs))))

(def ^:private junit-derived-name-pattern #"^(.+): ([^:]+)\.([^:\.]+)$")
(def ^:private rspec-derived-name-pattern #"^(.+): ([^:]+)$")

(defn- extract-junit-style-name [full-name]
  (when-let [match (re-matches junit-derived-name-pattern full-name)]
    (let [suites (nth match 1)
          classname (nth match 2)
          test-name (nth match 3)]
      (-> {:suite suites
           :name test-name
           :classname classname}))))

(defn- extract-rspec-style-name [full-name]
  (let [match (re-matches rspec-derived-name-pattern full-name)
        classname (nth match 1)
        test-name (nth match 2)]
    (-> {:suite "<empty>"
         :name test-name
         :classname classname})))


(defn- test-names-from-junit-source? [tests]
  (not-any? nil? (map (fn [{name :name}]
                        (re-matches junit-derived-name-pattern name))
                      tests)))

(defn- guess-test-name-pattern [tests]
  (if (test-names-from-junit-source? tests)
    extract-junit-style-name
    extract-rspec-style-name))

(defn- convert-status [ignored status]
  (if ignored
    "skipped"
    (if (= status "SUCCESS")
      "pass"
      "fail")))

(defn- convert-test [parse-teamcity-test-name {:keys [name status ignored duration]}]
  (-> (parse-teamcity-test-name name)
      (assoc :status (convert-status ignored status))
      (cond-> duration
        (assoc :runtime duration))))

(defn- convert-test-results [tests]
  (let [parse-teamcity-test-name (guess-test-name-pattern tests)]
    (->> tests
         (map #(convert-test parse-teamcity-test-name %))
         (group-by :suite)
         (map (fn [[suite tests]]
                {:name suite
                 :children (map #(dissoc % :suite) tests)}))
         seq)))

(defn- job-name [{:keys [project-name job-name]}]
  (format "%s %s" project-name job-name))

(defn teamcity-build->buildviz-build [{:keys [build tests] :as build-info}]
  {:job-name (job-name build-info)
   :build-id (:number build)
   :build (convert-build build)
   :test-results (convert-test-results tests)})
