(ns buildviz.safe-filenames
  (:require [clojure.string :as str]))


(defn- hex [integer]
  (format "%02x" integer))

(defn- unhex [hex-string]
  (Integer/parseInt hex-string 16))


(def invalid-parts #"(?i)[\x00-\x1F\x80-\x9f\/\\:\*\?\"<>\|]|(?:^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$)|(?:[\. ]+$)")

(defn- percent-encode [string]
  (->> string
       char-array
       (map int)
       (map #(str "%" (hex %)))
       (str/join "")))

(defn encode [filename]
  (str/replace filename invalid-parts percent-encode))


(def entity #"%..")

(defn- decode-entity [entity]
  (str (char (unhex (subs entity 1)))))

(defn decode [filename]
  (str/replace filename entity decode-entity))
