(ns buildviz.csv-test
  (:use clojure.test
        buildviz.csv))

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
           (export ["col 1", "col,2", "col3"])))))
