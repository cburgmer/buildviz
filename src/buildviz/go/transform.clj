(ns buildviz.go.transform
  (:require [buildviz.go.aggregate :as goaggregate]
            [clojure.string :as str]))

(defn- job-name [pipeline-name stage-name]
  (format "%s :: %s" pipeline-name stage-name))

(defn- build-id [pipeline-run stage-run]
  (if (= "1" stage-run)
    (str pipeline-run)
    (format "%s (Run %s)" pipeline-run stage-run)))


(defn- previous-stage-trigger [pipeline-name pipeline-run stage-name stages]
  (when-let [previous-stage (last (take-while #(not= stage-name (:name %)) stages))]
    [{:job-name (job-name pipeline-name (:name previous-stage))
      :build-id (build-id pipeline-run (:counter previous-stage))}]))


(defn- pipeline-build-cause [{:keys [modifications material changed]}]
  (when (and changed (= "Pipeline" (:type material)))
    (let [revision-tokens (str/split (:revision (first modifications)) #"/")
          pipeline-name (nth revision-tokens 0)
          pipeline-run (nth revision-tokens 1)
          stage-name (nth revision-tokens 2)
          stage-run (nth revision-tokens 3)]
      {:job-name (job-name pipeline-name stage-name)
       :build-id (build-id pipeline-run stage-run)})))

(defn- pipeline-material-triggers [pipeline-instance stages]
  (let [revisions (:material_revisions (:build_cause pipeline-instance))]
    (keep pipeline-build-cause revisions)))


(defn- first-stage? [stage-name stages]
  (= stage-name (:name (first stages))))

(defn- rerun? [stage-run]
  (not= "1" stage-run))

(defn- build-triggers-for-stage-instance [pipeline-name pipeline-run stage-name stage-run pipeline-instance]
  (let [stages (:stages pipeline-instance)]
    (if (first-stage? stage-name stages)
      (pipeline-material-triggers pipeline-instance stages)
      (when-not (rerun? stage-run)
        (previous-stage-trigger pipeline-name pipeline-run stage-name stages)))))


(defn- revision->input [{:keys [modifications material]}]
  {:revision (:revision (first modifications))
   :sourceId (:id material)})

(defn- inputs-for-stage-instance [pipeline-instance]
  (let [revisions (:material_revisions (:build_cause pipeline-instance))]
    (map revision->input revisions)))


(defn stage-instances->builds [{:keys [pipeline-name pipeline-run stage-name stage-run pipeline-instance] :as stage-instance}]
  (let [{outcome :outcome
         start :start
         end :end
         junit-xml :junit-xml} (goaggregate/aggregate-jobs-for-stage stage-instance)
        inputs (inputs-for-stage-instance pipeline-instance)
        triggered-by (seq (build-triggers-for-stage-instance pipeline-name pipeline-run stage-name stage-run pipeline-instance))]
    {:job-name (job-name pipeline-name stage-name)
     :build-id (build-id pipeline-run stage-run)
     :junit-xml junit-xml
     :build (cond-> {:start start
                     :end end
                     :outcome outcome
                     :inputs inputs}
              triggered-by (assoc :triggered-by triggered-by))}))
