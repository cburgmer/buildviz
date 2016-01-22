(ns buildviz.util.http-test
(:require [buildviz.util.http :as http]
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

  (testing "should not apply Last-Modified header on 404"
    (let [request {:request-method :get}
          handler (fn [_] (hash-map :status 404 :headers {} :body "something"))]
      (is (= {:status 404
              :headers {}
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
              :headers {}
              :body nil}
             (http/not-modified-request handler date request)))))

  (testing "should return NOT MODIFIED even if both timestamps match exactly"
    (let [request {:request-method :get
                   :headers {"If-Modified-Since" "Tue, 14 Oct 1986 04:03:27 GMT"}}
          handler (fn [_] (hash-map :status 200 :headers {} :body "something"))]
      (is (= {:status 304
              :headers {}
              :body nil}
             (http/not-modified-request handler date request))))))


(deftest test-respond-with-json
  (testing "should translate dash-keywords to JSON camel-case"
    (is (= "{\"camelCase\":\"a-value\"}"
           (:body (http/respond-with-json {:camel-case "a-value"}))))))


(deftest test-wrap-resource-format
  (testing "should set map request for matching format"
    (let [request {:uri "/the_url.json"
                   :headers {"accept" "*"}}
          fake-handler identity]
      (is (= {:uri "/the_url"
              :headers {"accept" "application/json"}}
             ((http/wrap-resource-format fake-handler {:json "application/json"}) request)))))

  (testing "should not touch request for unknown format"
    (let [request {:uri "/the_url.xml"
                   :headers {"accept" "*"}}
          fake-handler identity]
      (is (= {:uri "/the_url.xml"
              :headers {"accept" "*"}}
             ((http/wrap-resource-format fake-handler {:json "application/json"}) request)))))

  (testing "should not touch request without format"
    (let [request {:uri "/the_url"
                   :headers {"accept" "*"}}
          fake-handler identity]
      (is (= {:uri "/the_url"
              :headers {"accept" "*"}}
             ((http/wrap-resource-format fake-handler {:json "application/json"}) request)))))

  (testing "should ignore nil as key"
    (let [request {:uri "/the_url"
                   :headers {"accept" "*"}}
          fake-handler identity]
      (is (= {:uri "/the_url"
              :headers {"accept" "*"}}
             ((http/wrap-resource-format fake-handler {nil "application/json"}) request))))))
