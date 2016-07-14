(ns buildviz.storage
  (:require [buildviz.util.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- validate-names [& names]
  (doseq [name names]
    (when (re-find #"[\.\\/]" name)
      (throw (IllegalArgumentException. (format "Illegal name '%s'" name))))))


(defn store-build! [base-dir job-name build-id build-data]
  (validate-names job-name build-id)
  (let [job-dir (io/file base-dir job-name)]
    (.mkdirs job-dir)
    (let [build-file (io/file job-dir (str/join [build-id ".json"]))]
      (spit build-file (json/to-string build-data)))))


(defn- match-build-id [build-file]
  (last (re-matches #"(.*)\.json"
                    (.getName build-file))))

(defn- builds-for-job-dir [job-dir extract-build-id]
  (let [job-name (.getName job-dir)]
    (->> job-dir
       .listFiles
       (map #(vector (extract-build-id %) %))
       (remove (fn [[build-id file]] (nil? build-id)))
       (map (fn [[build-id file]]
              [job-name build-id file])))))

(defn load-builds [base-dir]
  (->> (io/file base-dir)
       .listFiles
       seq
       (filter #(.isDirectory %))
       (mapcat #(builds-for-job-dir % match-build-id))
       (reduce (fn [jobs [job-name build-id file]]
                 (assoc-in jobs
                           [job-name build-id]
                           (json/from-string (slurp file))))
               {})))


(defn store-testresults! [base-dir job-name build-id test-xml]
  (validate-names job-name build-id)
  (let [job-dir (io/file base-dir job-name)]
    (.mkdirs job-dir)
    (let [testresults-file (io/file job-dir (str/join [build-id ".xml"]))]
      (spit testresults-file test-xml))))

(defn load-testresults [base-dir job-name build-id]
  (validate-names job-name build-id)
  (let [file (io/file base-dir (str/join [job-name "/" build-id ".xml"]))]
    (when (.exists file)
      (slurp file))))
