(ns buildviz.junit-xml-test
  (:use clojure.test)
  (:require [buildviz.junit-xml :as junit-xml]))

(deftest Info
  (testing "is-ok?"
    (is (= true
           (junit-xml/is-ok? {:status :pass})))
    (is (= true
           (junit-xml/is-ok? {:status :skipped})))
    (is (= false
           (junit-xml/is-ok? {:status :fail})))
    (is (= false
           (junit-xml/is-ok? {:status :error})))))

(deftest Parsing
  (testing "parse-testsuites"
    (testing "status"
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "the class"
                           :status :fail}]}]
             (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"><failure/></testcase></testsuite>")))
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "the class"
                           :status :error}]}]
             (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"><error/></testcase></testsuite>")))
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "the class"
                           :status :skipped}]}]
             (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"><skipped/></testcase></testsuite>")))
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "the class"
                           :status :pass}]}]
             (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"></testcase></testsuite>"))))

    (testing "testsuite nesting"
      (is (= [{:name "a suite"
               :children [{:name "a sub suite"
                           :children [{:name "a test"
                                       :classname "the class"
                                       :status :pass}]}]}]
             (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testsuite name=\"a sub suite\"><testcase classname=\"the class\" name=\"a test\"></testcase></testsuite></testsuite>")))
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "the class"
                           :status :pass}]}]
             (junit-xml/parse-testsuites "<testsuites><testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"></testcase></testsuite></testsuites>")))
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "some class"
                           :status :pass}]}
              {:name "another suite"
               :children [{:name "another test"
                           :classname "the class"
                           :status :pass}]}]
             (junit-xml/parse-testsuites "<testsuites><testsuite name=\"a suite\"><testcase classname=\"some class\" name=\"a test\"></testcase></testsuite><testsuite name=\"another suite\"><testcase classname=\"the class\" name=\"another test\"></testcase></testsuite></testsuites>"))))

    (testing "optional runtime"
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "the class"
                           :status :pass
                           :runtime 1234}]}]
             (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\" time=\"1.234\"></testcase></testsuite>"))))

    (testing "ignored nodes"
      (is (= [{:name "a suite"
             :children [{:name "a test"
                         :classname "the class"
                         :status :pass}]}]
           (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><properties></properties><testcase classname=\"the class\" name=\"a test\"></testcase></testsuite>")))
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "the class"
                           :status :pass}]}]
             (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"></testcase><system-out>some sys out</system-out></testsuite>")))
      (is (= [{:name "a suite"
               :children [{:name "a test"
                           :classname "the class"
                           :status :pass}]}]
             (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"></testcase><system-err><![CDATA[]]></system-err></testsuite>"))))))
