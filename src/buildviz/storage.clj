(ns buildviz.storage
  (:require [cheshire.core :as j]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.nippy :as nippy]
            [clojure.tools.logging :as log]))

(import '[java.io DataInputStream DataOutputStream])

(defn store-build! [job-name build-id build-data base-dir]
  (let [job-dir (io/file base-dir job-name)]
    (.mkdirs job-dir)
    (let [build-file (io/file job-dir (str/join [build-id ".json"]))]
      (spit build-file (j/generate-string build-data)))))


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
                           (j/parse-string (slurp file) true)))
               {})))


(defn store-testresults! [job-name build-id test-xml base-dir]
  (let [job-dir (io/file base-dir job-name)]
    (.mkdirs job-dir)
    (let [testresults-file (io/file job-dir (str/join [build-id ".xml"]))]
      (spit testresults-file test-xml))))

(defn- match-build-id-for-testresults [build-file]
  (last (re-matches #"(.*)\.xml"
                    (.getName build-file))))

(defn load-all-testresults [base-dir]
  (->> (io/file base-dir)
       .listFiles
       seq
       (filter #(.isDirectory %))
       (mapcat #(builds-for-job-dir % match-build-id-for-testresults))
       (reduce (fn [jobs [job-name build-id file]]
                 (assoc-in jobs
                           [job-name build-id]
                           (slurp file)))
               {})))


(defn store! [jobs filename]
  (log/info (format "Persisting to %s" filename))
  (with-open [w (io/output-stream filename)]
    (nippy/freeze-to-out! (DataOutputStream. w) jobs)))

(defn load-from-file [filename]
  (if (.exists (io/file filename))
    (with-open
      [r (io/input-stream filename)]
      (nippy/thaw-from-in! (DataInputStream. r)))
    {}))
