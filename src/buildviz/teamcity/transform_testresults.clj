(ns buildviz.teamcity.transform-testresults
  (:require [clj-time
             [coerce :as tc]
             [format :as tf]]))

(def ^:private junit-derived-name-pattern #"^(?s)(?:(.+): )?([^:]+)\.(.+)$")
(def ^:private rspec-derived-name-pattern #"^(.+): (.+)$")

(defn- extract-junit-style-name [full-name]
  (when-let [match (re-matches junit-derived-name-pattern full-name)]
    (let [suite (nth match 1)
          classname (nth match 2)
          test-name (nth match 3)]
      {:suite (or suite "<no suite>")
       :name test-name
       :classname classname})))

;; RSpec reporter is missing some information https://youtrack.jetbrains.com/issue/TW-45063
(defn- extract-rspec-style-name [full-name]
  (let [match (re-matches rspec-derived-name-pattern full-name)
        classname (nth match 1)
        test-name (nth match 2)]
    {:suite "<empty>"
     :name test-name
     :classname classname}))


(defn- test-names-from-junit-source? [tests]
  (not-any? nil? (map (fn [{name :name}]
                        (re-matches junit-derived-name-pattern name))
                      tests)))

;; REST API doesn't separate test suite, package name and short test name
;; https://youtrack.jetbrains.com/issue/TW-40309, https://youtrack.jetbrains.com/issue/TW-42683
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

(defn- test-duration [duration]
  (or duration 0)) ; Work around https://youtrack.jetbrains.com/issue/TW-45065

(defn- convert-test [parse-teamcity-test-name {:keys [name status ignored duration]}]
  (-> (parse-teamcity-test-name name)
      (assoc :status (convert-status ignored status))
      (assoc :runtime (test-duration duration))))

(defn convert-test-results [tests]
  (let [parse-teamcity-test-name (guess-test-name-pattern tests)]
    (->> tests
         (map #(convert-test parse-teamcity-test-name %))
         (group-by :suite)
         (map (fn [[suite tests]]
                {:name suite
                 :children (map #(dissoc % :suite) tests)}))
         seq)))
