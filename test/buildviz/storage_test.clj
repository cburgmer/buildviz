(ns buildviz.storage-test
  (:use clojure.test
        buildviz.storage)
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]))

(defn- create-tmp-dir [prefix] ; http://stackoverflow.com/questions/617414/create-a-temporary-directory-in-java
  (let [tmp-file (java.io.File/createTempFile prefix ".tmp")]
    (.delete tmp-file)
    (.getPath tmp-file)))


(deftest test-store-build!
  (testing "should persist json"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (store-build! "aJob", "aBuild" {:start 42} data-dir)
      (is (= "{\"start\":42}"
             (slurp (io/file data-dir "aJob/aBuild.json")))))))

(deftest test-load-builds
  (testing "should return json"
    (let [data-dir (create-tmp-dir "buildviz-data")
          build-file (io/file data-dir "someJob/someBuild.json")]
      (.mkdirs (.getParentFile build-file))
      (spit build-file "{\"outcome\":\"fail\"}")
      (is (= {"someJob" {"someBuild" {:outcome "fail"}}}
             (load-builds data-dir))))))

(deftest test-store-testresults!
  (testing "should persist XML"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (store-testresults! "anotherJob" "anotherBuild" "<xml>" data-dir)
      (is (= "<xml>"
             (slurp (io/file data-dir "anotherJob/anotherBuild.xml")))))))

(deftest test-load-all-testresults
  (testing "should return XML"
    (let [data-dir (create-tmp-dir "buildviz-data")
          testresults-file (io/file data-dir "yetAnotherJob/yetAnotherBuild.xml")]
      (.mkdirs (.getParentFile testresults-file))
      (spit testresults-file "<thexml>")
      (is (= {"yetAnotherJob" {"yetAnotherBuild" "<thexml>"}}
             (load-all-testresults data-dir))))))
