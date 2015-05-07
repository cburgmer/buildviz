(defproject buildviz "0.1.0-SNAPSHOT"
  :description "Transparency for your build pipeline's results and runtime."
  :url "http://example.com/FIXME"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring/ring-json "0.2.0"]
                 [compojure "1.1.6"]]
  :plugins [[lein-ring "0.8.10"]]
  :ring {:handler buildviz.handler/app
         :nrepl {:start? true
                 :port 9998}}
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [cheshire "5.4.0"]
                                  [ring-mock "0.1.5"]]}})
