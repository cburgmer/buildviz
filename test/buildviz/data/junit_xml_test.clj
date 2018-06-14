(ns buildviz.data.junit-xml-test
  (:require [buildviz.data.junit-xml :as junit-xml]
            [clojure
             [test :refer :all]
             [walk :refer :all]]))

(deftest test-parse-testsuites
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

  (testing "invalid input"
    (is (thrown? IllegalArgumentException (postwalk identity (junit-xml/parse-testsuites "<testsuite><testcase classname=\"the class\" name=\"a test\"></testcase></testsuite>"))))
    (is (thrown? IllegalArgumentException (postwalk identity (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\"></testcase></testsuite>"))))
    (is (thrown? IllegalArgumentException (postwalk identity (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase name=\"a test\"></testcase></testsuite>")))))

  (testing "'class' instead of 'classname'" ; https://phpunit.de/manual/current/en/logging.html
    (is (= [{:name "a suite"
             :children [{:name "a test"
                         :classname "the class"
                         :status :pass}]}]
           (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase class=\"the class\" name=\"a test\"></testcase></testsuite>"))))

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

  (testing "should understand human formatted time"
    (is (= 14029255
           (-> (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\" time=\"14,029.255\"></testcase></testsuite>")
               first
               :children
               first
               :runtime))))

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
           (junit-xml/parse-testsuites "<testsuite name=\"a suite\"><testcase classname=\"the class\" name=\"a test\"></testcase><system-err><![CDATA[]]></system-err></testsuite>")))))


(deftest test-serialize-testsuites
  (testing "should serialize suite"
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"The Suite\"><testcase name=\"The Test\" classname=\"The Class\" time=\"0.042\"></testcase></testsuite></testsuites>"
           (junit-xml/serialize-testsuites [{:name "The Suite"
                                             :children [{:name "The Test"
                                                         :classname "The Class"
                                                         :runtime 42
                                                         :status "pass"}]}]))))

  (testing "should serialize failing test"
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"The Suite\"><testcase name=\"The Test\" classname=\"The Class\" time=\"0.042\"><failure></failure></testcase></testsuite></testsuites>"
           (junit-xml/serialize-testsuites [{:name "The Suite"
                                             :children [{:name "The Test"
                                                         :classname "The Class"
                                                         :runtime 42
                                                         :status "fail"}]}]))))

  (testing "should serialize errored test"
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"The Suite\"><testcase name=\"The Test\" classname=\"The Class\" time=\"0.042\"><error></error></testcase></testsuite></testsuites>"
           (junit-xml/serialize-testsuites [{:name "The Suite"
                                             :children [{:name "The Test"
                                                         :classname "The Class"
                                                         :runtime 42
                                                         :status "error"}]}]))))

  (testing "should serialize skipped test"
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"The Suite\"><testcase name=\"The Test\" classname=\"The Class\" time=\"0.042\"><skipped></skipped></testcase></testsuite></testsuites>"
           (junit-xml/serialize-testsuites [{:name "The Suite"
                                             :children [{:name "The Test"
                                                         :classname "The Class"
                                                         :runtime 42
                                                         :status "skipped"}]}]))))

  (testing "should serialize nested testsuite"
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"The Suite\"><testsuite name=\"Nested Suite\"><testcase name=\"The Test\" classname=\"The Class\" time=\"0.042\"></testcase></testsuite></testsuite></testsuites>"
           (junit-xml/serialize-testsuites [{:name "The Suite"
                                             :children [{:name "Nested Suite"
                                                         :children [{:name "The Test"
                                                                     :classname "The Class"
                                                                     :runtime 42
                                                                     :status "pass"}]}]}]))))

  (testing "should handle optional runtime"
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"The Suite\"><testcase name=\"The Test\" classname=\"The Class\"></testcase></testsuite></testsuites>"
           (junit-xml/serialize-testsuites [{:name "The Suite"
                                             :children [{:name "The Test"
                                                         :classname "The Class"
                                                         :status "pass"}]}]))))

  (testing "should deal with empty testsuites"
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites></testsuites>"
           (junit-xml/serialize-testsuites []))))

  (testing "should deal with empty testsuite"
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites><testsuite name=\"The Suite\"></testsuite></testsuites>"
           (junit-xml/serialize-testsuites [{:name "The Suite"
                                             :children []}])))))

(deftest test-junit-to-string
  (testing "should serialize empty testsuite"
    (is (= "The Suite\n"
           (with-out-str (junit-xml/to-string [{:name "The Suite"
                                                :children []}])))))
  (testing "should serialize simple testsuite"
    (is (= "The Suite\n  The Class.The Test 42ms pass\n"
           (with-out-str (junit-xml/to-string [{:name "The Suite"
                                                :children [{:name "The Test"
                                                            :classname "The Class"
                                                            :runtime 42
                                                            :status "pass"}]}])))))
  (testing "should serialize nested testsuite"
    (is (= "The Suite\n  Nested Suite\n    The Class.The Test 42ms pass\n"
           (with-out-str (junit-xml/to-string [{:name "The Suite"
                                                :children [{:name "Nested Suite"
                                                            :children [{:name "The Test"
                                                                        :classname "The Class"
                                                                        :runtime 42
                                                                        :status "pass"}]}]}]))))))
