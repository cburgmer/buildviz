(ns buildviz.go.aggregate
  (:require [clojure.data.xml :as xml]
            [clojure.tools.logging :as log]))

(defn- testsuite? [elem]
  (= :testsuite (:tag elem)))

(defn- testsuite-list [junit-xml]
  (let [root (xml/parse-str junit-xml)]
    (if (testsuite? root)
      (list root)
      (:content root))))

(defn- aggregate-junit-xml-testsuites [junit-xml-list]
  (xml/emit-str (apply xml/element (cons :testsuites
                                         (cons {}
                                               (mapcat testsuite-list junit-xml-list))))))

(defn- aggregate-junit-xml [{:keys [pipeline-name pipeline-run stage-name
                                    stage-run job-instances]}]
  (let [all-junit-xml (map :junit-xml job-instances)
        junit-xml-list (remove nil? all-junit-xml)]
    (when-not (empty? junit-xml-list)
      (when-not (= (count junit-xml-list) (count all-junit-xml))
        (log/infof "Unable to accumulate all JUnit XML for jobs of %s %s (%s %s)"
                   pipeline-name stage-name pipeline-run stage-run))
      (try
        (aggregate-junit-xml-testsuites junit-xml-list)
        (catch Exception e
          (log/errorf e "Unable to aggregate testresults for %s %s (%s %s)" pipeline-name stage-name pipeline-run stage-run))))))


(defn- aggregate-build-times [job-instances]
  (let [start-times (map :start job-instances)
        end-times (map :end job-instances)]
    (if (and (empty? (filter nil? end-times))
             (seq end-times))
      {:start (apply min start-times)
       :end (apply max end-times)}
      {})))

(defn- aggregate-builds [job-instances]
  (let [outcomes (map :outcome job-instances)
        accumulated-outcome (if (every? #(= "pass" %) outcomes)
                              "pass"
                              "fail")]
    (assoc (aggregate-build-times job-instances)
           :outcome accumulated-outcome)))


(defn- ignore-old-runs-for-rerun-stages [job-instances stage-run]
  (filter #(= stage-run (:actual-stage-run %)) job-instances))

(defn- aggregate-build [{:keys [stage-run stage-name job-instances]}]
  (-> job-instances
      (ignore-old-runs-for-rerun-stages stage-run)
      aggregate-builds
      (assoc :name stage-name)))

(defn aggregate-jobs-for-stage [stage-instance]
  (let [aggregated-junit-xml (aggregate-junit-xml stage-instance)
        aggregated-build (aggregate-build stage-instance)]
    (assoc stage-instance
           :job-instances (list (assoc aggregated-build
                                       :junit-xml aggregated-junit-xml)))))
