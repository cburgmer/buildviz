(ns buildviz.storage-test
  (:use clojure.test
        buildviz.storage)
  (:require [cheshire.core :as json]))

(deftest Store
  (let [temp-file (.getPath (java.io.File/createTempFile "test-serialization" ".tmp"))]

    (testing "read/write"
      (let [jobs {}]
        (store! jobs temp-file)
        (is (= (load temp-file)
               jobs)))

      (let [jobs {"someBuild" {"1" {:start 42 :end 50 :outcome "pass"}}}]
        (store! jobs temp-file)
        (is (= (load temp-file)
               jobs)))

      (is (= {}
             (load "non-existing-file"))))))
