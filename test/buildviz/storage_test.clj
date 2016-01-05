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
      (store-build! data-dir "aJob" "aBuild" {:start 42})
      (is (= "{\"start\":42}"
             (slurp (io/file data-dir "aJob/aBuild.json"))))))

  (testing "should transform to camel-case keys"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (store-build! data-dir "aJob" "aBuild" {:start 42 :inputs [{:revision "abcd" :source-id 42}]})
      (is (= "{\"start\":42,\"inputs\":[{\"revision\":\"abcd\",\"sourceId\":42}]}"
             (slurp (io/file data-dir "aJob/aBuild.json")))))))

(deftest test-load-builds
  (testing "should return json"
    (let [data-dir (create-tmp-dir "buildviz-data")
          build-file (io/file data-dir "someJob/someBuild.json")]
      (.mkdirs (.getParentFile build-file))
      (spit build-file "{\"outcome\":\"fail\"}")
      (is (= {"someJob" {"someBuild" {:outcome "fail"}}}
             (load-builds data-dir)))))

  (testing "should transform from camel-case keys"
    (let [data-dir (create-tmp-dir "buildviz-data")
          build-file (io/file data-dir "someJob/someBuild.json")]
      (.mkdirs (.getParentFile build-file))
      (spit build-file "{\"outcome\":\"fail\",\"inputs\":[{\"revision\":\"abcd\",\"sourceId\":42}]}")
      (is (= {"someJob" {"someBuild" {:outcome "fail" :inputs [{:revision "abcd" :source-id 42}]}}}
             (load-builds data-dir))))))

(deftest test-store-testresults!
  (testing "should persist XML"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (store-testresults! data-dir "anotherJob" "anotherBuild" "<xml>")
      (is (= "<xml>"
             (slurp (io/file data-dir "anotherJob/anotherBuild.xml")))))))

(deftest test-load-testresults
  (testing "should return XML"
    (let [data-dir (create-tmp-dir "buildviz-data")
          testresults-file (io/file data-dir "yetAnotherJob/yetAnotherBuild.xml")]
      (.mkdirs (.getParentFile testresults-file))
      (spit testresults-file "<thexml>")
      (is (= "<thexml>"
             (load-testresults data-dir "yetAnotherJob" "yetAnotherBuild")))))

  (testing "handle missing XML"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (.mkdirs (io/file data-dir))
      (is (= nil
             (load-testresults data-dir "yetAnotherJob" "yetAnotherBuild"))))))
