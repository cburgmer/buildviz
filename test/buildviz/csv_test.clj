(ns buildviz.csv-test
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc])
  (:use clojure.test
        buildviz.csv))

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
           (format-timestamp (tc/to-long a-datetime))))))
