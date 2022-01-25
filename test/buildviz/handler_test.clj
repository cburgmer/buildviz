(ns buildviz.handler-test
  (:require [buildviz
             [handler :as handler]
             [test-utils :refer :all]]
            [clojure.test :refer :all]))

(deftest EntryPoint
  (testing "GET to /"
    (is (= 302
           (:status (get-request (the-app) "/"))))
    (is (= "/index.html"
           (get (:headers (get-request (the-app) "/")) "Location"))))

  (testing "GET to /unknown"
    (is (= 404
           (:status (get-request (the-app) "/unknown"))))))
