(ns buildviz.util.csv-test
(:require [buildviz.util.csv :refer :all]
[clj-time
[coerce :as tc]
[core :as t]]
[clojure.test :refer :all]))

(def a-datetime (t/from-time-zone (t/date-time 1986 10 14 4 3 27 456) (t/default-time-zone)))

(deftest CSV
  (testing "export"
    (is (= "col 1,col 2"
           (export ["col 1", "col 2"])))
    (is (= "col 1"
           (export ["col 1"])))
    (is (= ",col 2"
           (export [nil, "col 2"])))
    (is (= ""
           (export [])))
    (is (= "\"col,1\""
           (export ["col,1"])))
    (is (= "\"col\"\"1\""
           (export ["col\"1"])))
    (is (= "\"col,\"\"1\""
           (export ["col,\"1"])))
    (is (= "col 1,\"col,2\",col3"
           (export ["col 1", "col,2", "col3"]))))

  (testing "format-timestamp"
    (is (= "1986-10-14 04:03:27"
           (format-timestamp (tc/to-long a-datetime)))))

  (testing "format-duration"
    (is (= (format "%.8f" 1.)
           (format-duration (* 24 60 60 1000))))
    (is (= 1
           (Math/round (* 24. 60 60 1000
                          (Float/parseFloat
                           (format-duration 1))))))))
