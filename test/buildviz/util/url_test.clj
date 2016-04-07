(ns buildviz.util.url-test
  (:require [buildviz.util.url :as sut]
            [clojure.test :refer :all]))

(deftest test-url-with-plain-text-password
  (testing "should return simple URL"
    (is (= "http://localhost:3000"
           (sut/with-plain-text-password (sut/url "http://localhost:3000")))))
  (testing "should return URL with password"
    (is (= "http://admin:admin@localhost:3000"
           (sut/with-plain-text-password (sut/url "http://admin:admin@localhost:3000"))))))

(deftest test-url-toString
  (testing "should return simple URL"
    (is (= "http://localhost:3000"
           (str (sut/url "http://localhost:3000")))))
  (testing "should return URL with password hidden"
    (is (= "http://admin:*****@localhost:3000"
           (str (sut/url "http://admin:elaboratePassword@localhost:3000")))))
  (testing "should return URL with username but no password"
    (is (= "http://admin@localhost:3000"
           (str (sut/url "http://admin@localhost:3000")))))
  (testing "should handle full URL"
    (is (= "https://username:*****@host:1234/some/path/?query#fragment"
           (str (sut/url "https://username:password@host:1234/some/path/?query#fragment"))))))
