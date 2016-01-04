(ns buildviz.jenkins.transform)

(defn- jenkins-test-case->buildviz-test-case [{:keys [:className :name :duration :status]}]
  {:classname className
   :name name
   :runtime (Math/round (* duration 1000))
   :status (case status
             "PASSED" "pass"
             "FIXED" "pass"
             "REGRESSION" "fail"
             "FAILED" "fail"
             "SKIPPED" "skipped")})

(defn- jenkins-suite->buildviz-suite [{:keys [:name :cases]}]
  {:name name
   :children (map jenkins-test-case->buildviz-test-case cases)})

(defn- convert-test-results [{test-report :test-report}]
  (when test-report
    (->> (get test-report :suites)
         (map jenkins-suite->buildviz-suite))))

(defn- git-input-from [{actions :actions}]
  (when-let [git-revision-info (first (filter :lastBuiltRevision actions))]
    {:revision (get-in git-revision-info [:lastBuiltRevision :SHA1])
     :source_id (get-in git-revision-info [:remoteUrls 0])}))

(defn- with-inputs [map jenkins-build]
  (let [git-input (git-input-from jenkins-build)]
    (if git-input
      (assoc map :inputs [git-input])
      map)))

(defn- convert-build [{:keys [timestamp duration result] :as build}]
  (-> {:start timestamp
       :end (+ timestamp duration)
       :outcome (if (= result "SUCCESS")
                  "pass"
                  "fail")}
      (with-inputs build)))

(defn jenkins-build->buildviz-build [{:keys [job-name number] :as build}]
  {:job-name job-name
   :build-id number
   :build (convert-build build)
   :test-results (convert-test-results build)})
