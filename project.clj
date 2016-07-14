(defproject buildviz "0.10.1"
  :description "Transparency for your build pipeline's results and runtime."
  :url "https://github.com/cburgmer/buildviz"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring/ring-json "0.2.0"]
                 [ring-middleware-accept "2.0.3"]
                 [compojure "1.1.6"]
                 [bigml/closchema "0.6.1"]
                 [clj-http "1.1.2"]
                 [clj-time "0.9.0"]
                 [cheshire "5.4.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "0.3.3"]
                 [intervox/clj-progress "0.2.1"]
                 [wharf "0.2.0-20141115.032457-2"]]
  :plugins [[lein-ring "0.9.3"]
            [lein-npm "0.6.1"]]
  :npm {:dependencies [[d3 "3.5.5"]
                       [moment "2.10.6"]
                       [moment-duration-format "1.3.0"]]
        :root "resources/public"}
  :ring {:handler buildviz.main/app
         :init buildviz.main/help}
  :aot [buildviz.go.sync
        buildviz.jenkins.sync
        buildviz.teamcity.sync]
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]
                                  [clj-http-fake "1.0.2"]]}})
