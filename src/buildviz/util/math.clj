(ns buildviz.util.math)

(defn avg [series]
  (Math/round (float (/ (reduce + series) (count series)))))
