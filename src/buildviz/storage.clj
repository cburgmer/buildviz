(ns buildviz.storage
  (:require [buildviz.util.json :as json]
            [buildviz.safe-filenames :as safe-filenames]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- filename [& parts]
  (safe-filenames/encode (apply str parts)))

(defn store-build! [base-dir job-name build-id build-data]
  (let [job-dir (io/file base-dir (filename job-name))]
    (.mkdirs job-dir)
    (let [build-file (io/file job-dir (filename build-id ".json"))]
      (spit build-file (json/to-string build-data)))))


(defn- match-build-id [build-file]
  (last (re-matches #"(.*)\.json"
                    (safe-filenames/decode (.getName build-file)))))

(defn- builds-for-job-dir [job-dir extract-build-id]
  (let [job-name (safe-filenames/decode (.getName job-dir))]
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
  (let [job-dir (io/file base-dir (filename job-name))]
    (.mkdirs job-dir)
    (let [testresults-file (io/file job-dir (filename build-id ".xml"))]
      (spit testresults-file test-xml))))

(defn load-testresults [base-dir job-name build-id]
  (let [file (io/file base-dir (str/join [(filename job-name) "/" (filename build-id ".xml")]))]
    (when (.exists file)
      (slurp file))))
