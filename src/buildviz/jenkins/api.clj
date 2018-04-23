(ns buildviz.jenkins.api
  (:require [cheshire.core :as j]
            [buildviz.util.url :as url]
            [clj-http.client :as client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [uritemplate-clj.core :as templ]))

(defn- get-json [jenkins-url relative-url]
  (log/info (format "Retrieving %s" relative-url))
  (j/parse-string (:body (client/get (string/join [(url/with-plain-text-password jenkins-url) relative-url]))) true))

(defn get-jobs [jenkins-url]
  (let [response (get-json jenkins-url "/api/json")]
    (map :name (get response :jobs))))


(defn- builds-response->builds [response job-name]
  (->> (get response :allBuilds)
       (map #(assoc % :job-name job-name))))

(def pagination-size 10)

(defn- get-builds-starting-from [jenkins-url job-name offset]
  (let [offset-end (+ offset pagination-size)
        response (get-json jenkins-url
                           (templ/uritemplate "/job{/job}/api/json?tree=allBuilds[number,timestamp,duration,result,actions[lastBuiltRevision[SHA1],remoteUrls,parameters[name,value],causes[upstreamProject,upstreamBuild,userId]]]%7B{offset},{offsetEnd}%7D"
                                              {"job" job-name
                                               "offset" offset
                                               "offsetEnd" offset-end}))
        builds (builds-response->builds response job-name)]
    (if (> pagination-size (count builds))
      builds
      (let [next-offset (+ offset (count builds))]
        (concat builds
                (lazy-seq (get-builds-starting-from jenkins-url job-name next-offset)))))))

(defn get-builds [jenkins-url job-name]
  (get-builds-starting-from jenkins-url job-name 0))

(defn get-test-report [jenkins-url job-name build-number]
  (let [test-report-url (templ/uritemplate "/job{/job}{/build}/testReport/api/json"
                                           {"job" job-name "build" build-number})]
    (try
      (get-json jenkins-url test-report-url)
      (catch Exception e
        (if (or (not (ex-data e))
                (not= 404 (:status (ex-data e))))
          (log/errorf e "Unable to get test report from %s" test-report-url))))))
