(defproject buildviz "0.1.0"
  :description "Transparency for your build pipeline's results and runtime."
  :url "https://github.com/cburgmer/buildviz"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring/ring-json "0.2.0"]
                 [ring-middleware-accept "2.0.3"]
                 [compojure "1.1.6"]
                 [com.taoensso/nippy "2.9.0"]]
  :plugins [[lein-ring "0.9.3"]
            [lein-exec "0.3.4"]
            [lein-npm "0.5.0"]]
  :node-dependencies [[d3 "3.5.5"]]
  :npm-root "resources/public/js"
  :ring {:handler buildviz.handler/app
         :nrepl {:start? true
                 :port 9998}}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [cheshire "5.4.0"]
                                  [ring-mock "0.1.5"]]}})
