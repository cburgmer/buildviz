(ns buildviz.storage-test
  (:require [buildviz.storage :as storage]
            [clojure.java.io :as io]
            [clojure.test :refer :all]))

(defn- create-tmp-dir [prefix] ; http://stackoverflow.com/questions/617414/create-a-temporary-directory-in-java
  (let [tmp-file (java.io.File/createTempFile prefix ".tmp")]
    (.delete tmp-file)
    (.getPath tmp-file)))


(deftest test-store-build!
  (testing "should persist json"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (storage/store-build! data-dir "aJob" "aBuild" {:start 42})
      (is (= "{\"start\":42}"
             (slurp (io/file data-dir "aJob/aBuild.json"))))))

  (testing "should transform to camel-case keys"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (storage/store-build! data-dir "aJob" "aBuild" {:start 42 :inputs [{:revision "abcd" :source-id 42}]})
      (is (= "{\"start\":42,\"inputs\":[{\"revision\":\"abcd\",\"sourceId\":42}]}"
             (slurp (io/file data-dir "aJob/aBuild.json"))))))

  (testing "should blow up on illegal characters"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (is (thrown? IllegalArgumentException (storage/store-build! data-dir ".aJob" "aBuild" {:start 42})))
      (is (thrown? IllegalArgumentException (storage/store-build! data-dir "aJob" ".aBuild" {:start 42})))
      (is (thrown? IllegalArgumentException (storage/store-build! data-dir "/aJob" "aBuild" {:start 42})))
      (is (thrown? IllegalArgumentException (storage/store-build! data-dir "\\aJob" "aBuild" {:start 42}))))))

(deftest test-load-builds
  (testing "should return json"
    (let [data-dir (create-tmp-dir "buildviz-data")
          build-file (io/file data-dir "someJob/someBuild.json")]
      (.mkdirs (.getParentFile build-file))
      (spit build-file "{\"outcome\":\"fail\"}")
      (is (= {"someJob" {"someBuild" {:outcome "fail"}}}
             (storage/load-builds data-dir)))))

  (testing "should transform from camel-case keys"
    (let [data-dir (create-tmp-dir "buildviz-data")
          build-file (io/file data-dir "someJob/someBuild.json")]
      (.mkdirs (.getParentFile build-file))
      (spit build-file "{\"outcome\":\"fail\",\"inputs\":[{\"revision\":\"abcd\",\"sourceId\":42}]}")
      (is (= {"someJob" {"someBuild" {:outcome "fail" :inputs [{:revision "abcd" :source-id 42}]}}}
             (storage/load-builds data-dir))))))

(deftest test-store-testresults!
  (testing "should persist XML"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (storage/store-testresults! data-dir "anotherJob" "anotherBuild" "<xml>")
      (is (= "<xml>"
             (slurp (io/file data-dir "anotherJob/anotherBuild.xml"))))))

  (testing "should blow up on illegal characters"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (is (thrown? IllegalArgumentException (storage/store-testresults! data-dir "anotherJob" "../anotherBuild" "<xml>"))))))

(deftest test-load-testresults
  (testing "should return XML"
    (let [data-dir (create-tmp-dir "buildviz-data")
          testresults-file (io/file data-dir "yetAnotherJob/yetAnotherBuild.xml")]
      (.mkdirs (.getParentFile testresults-file))
      (spit testresults-file "<thexml>")
      (is (= "<thexml>"
             (storage/load-testresults data-dir "yetAnotherJob" "yetAnotherBuild")))))

  (testing "handle missing XML"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (.mkdirs (io/file data-dir))
      (is (= nil
             (storage/load-testresults data-dir "yetAnotherJob" "yetAnotherBuild")))))

  (testing "should blow up on illegal characters"
    (let [data-dir (create-tmp-dir "buildviz-data")]
      (is (thrown? IllegalArgumentException (storage/load-testresults data-dir "yetAnotherJob/../" "yetAnotherBuild"))))))
