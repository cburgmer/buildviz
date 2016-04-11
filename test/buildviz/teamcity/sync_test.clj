(ns buildviz.teamcity.sync-test
  (:require [buildviz.teamcity.sync :as sut]
            [buildviz.util.url :as url]
            [cheshire.core :as j]
            [clj-http.fake :as fake]
            [clojure.test :refer :all]))

(defn- successful-json-response [body]
  (fn [_] {:status 200
           :body (j/generate-string body)}))

(deftest test-main
  (testing "should sync a build"
    (let [build-puts (atom {})]
      (fake/with-fake-routes {"http://teamcity:8000/httpAuth/app/rest/projects/the_project"
                              (successful-json-response {:buildTypes {:buildType [{:id "theId"
                                                                                   :projectName "theProject"
                                                                                   :name "theJob"}]}})
                              "http://teamcity:8000/httpAuth/app/rest/buildTypes/id:theId/builds/?fields=build(id,number,status,startDate,finishDate,revisions(revision(version,vcs-root-instance)))"
                              (successful-json-response {:build [{:id 42
                                                                  :number 2
                                                                  :status "SUCCESS"
                                                                  :startDate "20160410T041049+0000"
                                                                  :finishDate "20160410T041100+0000"
                                                                  :revisions []}]})
                              "http://teamcity:8000/httpAuth/app/rest/testOccurrences?locator=count:100,start:0,build:(id:42)"
                              (successful-json-response {:testOccurrences []})
                              #"http://buildviz:8010/builds/(.+)/(.+)"
                              (fn [req]
                                (swap! build-puts #(assoc % (:uri req) (j/parse-string (slurp (:body req)) true)))
                                {:status 200 :body ""})}
        (with-out-str (sut/sync-jobs (url/url "http://teamcity:8000") (url/url "http://buildviz:8010") ["the_project"]))
        (is (= {:start 1460261449000
                :end 1460261460000
                :outcome "pass"}
               (get @build-puts "/builds/theProject%20theJob/2")))))))
