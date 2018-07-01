(ns buildviz.go.transform
  (:require [clojure.string :as str]))

(defn- job-name [pipeline-name stage-name job-name]
  (if (= stage-name job-name)
    (format "%s :: %s" pipeline-name stage-name)
    (format "%s :: %s :: %s" pipeline-name stage-name job-name)))

(defn- build-id [pipeline-run stage-run]
  (if (= "1" stage-run)
    (str pipeline-run)
    (format "%s (Run %s)" pipeline-run stage-run)))


(defn- previous-stage-trigger [{:keys [pipeline-name pipeline-run stage-name stage-run]} stages]
  (when (= "1" stage-run)
    (when-let [previous-stage (last (take-while #(not= stage-name (:name %)) stages))]
      [{:job-name (job-name pipeline-name (:name previous-stage) (:name previous-stage))
        :build-id (build-id pipeline-run (:counter previous-stage))}])))

(defn- pipeline-build-cause [{:keys [modifications material changed]}]
  (when (and changed (= "Pipeline" (:type material)))
    (let [revision-tokens (str/split (:revision (first modifications)) #"/")]
      {:pipeline-name (nth revision-tokens 0)
       :pipeline-run (nth revision-tokens 1)
       :stage-name (nth revision-tokens 2)
       :stage-run (nth revision-tokens 3)})))

(defn- pipeline-material-triggers [{:keys [stage-name]} pipeline-instance stages]
  (let [revisions (:material_revisions (:build_cause pipeline-instance))
        triggers (keep pipeline-build-cause revisions)]
    (when (= stage-name (:name (first stages)))
      (map (fn [{:keys [pipeline-name pipeline-run stage-name stage-run]}]
             {:job-name (job-name pipeline-name stage-name stage-name)
              :build-id (build-id pipeline-run stage-run)})
           triggers))))

(defn build-triggers-for-stage-instance [pipeline-instance stage-instance]
  (let [stages (:stages pipeline-instance)]
    (seq (concat (previous-stage-trigger stage-instance stages)
                 (pipeline-material-triggers stage-instance pipeline-instance stages)))))



(defn- revision->input [{:keys [modifications material]}]
  {:revision (:revision (first modifications))
   :sourceId (:id material)})

(defn inputs-for-stage-instance [pipeline-instance]
  (let [revisions (:material_revisions (:build_cause pipeline-instance))]
    (map revision->input revisions)))




(defn stage-instances->builds [{:keys [pipeline-name pipeline-run stage-name stage-run inputs triggered-by job-instances]}]
  (map (fn [{outcome :outcome
             start :start
             end :end
             name :name
             junit-xml :junit-xml}]
         {:job-name (job-name pipeline-name stage-name name)
          :build-id (build-id pipeline-run stage-run)
          :junit-xml junit-xml
          :build (cond-> {:start start
                          :end end
                          :outcome outcome
                          :inputs inputs}
                   triggered-by (assoc :triggered-by triggered-by))})
       job-instances))
