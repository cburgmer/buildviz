(ns buildviz.jenkins.transform)

(defn- jenkins-test-case->buildviz-test-case [{:keys [className name duration status]}]
  {:classname className
   :name name
   :runtime (Math/round (* duration 1000))
   :status (case status
             "PASSED" "pass"
             "FIXED" "pass"
             "REGRESSION" "fail"
             "FAILED" "fail"
             "SKIPPED" "skipped")})

(defn- jenkins-suite->buildviz-suite [{:keys [name cases]}]
  {:name name
   :children (map jenkins-test-case->buildviz-test-case cases)})

(defn- convert-test-results [{test-report :test-report}]
  (when test-report
    (->> (get test-report :suites)
         (map jenkins-suite->buildviz-suite))))

(defn- git-input-from [{actions :actions}]
  (when-let [git-revision-info (first (filter :lastBuiltRevision actions))]
    [{:revision (get-in git-revision-info [:lastBuiltRevision :SHA1])
      :source-id (get-in git-revision-info [:remoteUrls 0])}]))

(defn- parameters-input-from [{actions :actions}]
  (->> (some :parameters actions)
       (map (fn [{:keys [name value]}]
              {:revision value
               :source-id name}))))

(defn- with-inputs [map jenkins-build]
  (if-let [inputs (seq (concat (git-input-from jenkins-build)
                               (parameters-input-from jenkins-build)))]
    (assoc map :inputs inputs)
    map))

(defn- manually-started-by-user? [causes]
  (some :userId causes))

(defn- triggered-by-from [{actions :actions}]
  (let [causes (mapcat :causes (filter :causes actions))]
    (when-not (manually-started-by-user? causes)
      (->> causes
           (filter :upstreamProject)
           (map (fn [cause]
                  {:job-name (:upstreamProject cause)
                   :build-id (.toString (:upstreamBuild cause))}))))))

(defn- with-triggered-by [map jenkins-build]
  (if-let [triggered-by (seq (triggered-by-from jenkins-build))]
    (assoc map :triggered-by triggered-by)
    map))

(defn- convert-build [{:keys [timestamp duration result] :as build}]
  (-> {:start timestamp
       :end (+ timestamp duration)
       :outcome (if (= result "SUCCESS")
                  "pass"
                  "fail")}
      (with-inputs build)
      (with-triggered-by build)))

(defn jenkins-build->buildviz-build [{:keys [job-name number] :as build}]
  {:job-name job-name
   :build-id number
   :build (convert-build build)
   :test-results (convert-test-results build)})
