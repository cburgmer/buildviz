(ns buildviz.util.math
  (:import [java.util Locale]))

(defn avg [series]
  (Math/round (float (/ (reduce + series) (count series)))))

;; https://stackoverflow.com/questions/44281495/format-string-representation-of-float-according-to-english-locale-with-clojure/44287007
(defn format-locale-neutral [fmt n]
  (let [locale (Locale. "en-US")]
    (String/format locale fmt (into-array Object [n]))))
