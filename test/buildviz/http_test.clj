(ns buildviz.http-test
  (:require [buildviz.http :as http]
            [clj-time.core :as t]
            [clojure.test :refer :all]))

(def date (t/date-time 1986 10 14 4 3 27 456))

(deftest test-wrap-not-modified
  (testing "should return handler response with last modified date if no if-modified-since header given"
    (let [request {:request-method :get}
          handler (fn [_] (hash-map :status 200 :headers {} :body "something"))]
      (is (= {:status 200
              :headers {"Last-Modified" "Tue, 14 Oct 1986 04:03:27 GMT"}
              :body "something" }
             (http/not-modified-request handler date request)))))

  (testing "should return handler response if modified since date given in if-modified-since"
    (let [request {:request-method :get
                   :headers {"If-Modified-Since" "Mon, 13 Oct 1986 05:16:08 GMT"}}
          handler (fn [_] (hash-map :status 200 :headers {} :body "something"))]
      (is (= {:status 200
              :headers {"Last-Modified" "Tue, 14 Oct 1986 04:03:27 GMT"}
              :body "something" }
             (http/not-modified-request handler date request)))))

  (testing "should return NOT MODIFIED if not modified since date given in if-modified-since"
    (let [request {:request-method :get
                   :headers {"If-Modified-Since" "Wed, 15 Oct 1986 14:26:58 GMT"}}
          handler (fn [_] (hash-map :status 200 :headers {} :body "something"))]
      (is (= {:status 304
              :headers {"Last-Modified" "Tue, 14 Oct 1986 04:03:27 GMT"}
              :body nil}
             (http/not-modified-request handler date request)))))

  (testing "should return NOT MODIFIED even if both timestamps match exactly"
    (let [request {:request-method :get
                   :headers {"If-Modified-Since" "Tue, 14 Oct 1986 04:03:27 GMT"}}
          handler (fn [_] (hash-map :status 200 :headers {} :body "something"))]
      (is (= {:status 304
              :headers {"Last-Modified" "Tue, 14 Oct 1986 04:03:27 GMT"}
              :body nil}
             (http/not-modified-request handler date request))))))

(deftest test-respond-with-json
  (testing "should translate dash-keywords to JSON camel-case"
    (is (= {:body {"camelCase" "a-value"}}
           (http/respond-with-json {:camel-case "a-value"})))))
