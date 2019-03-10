(defproject buildviz "0.14.1"
  :description "Transparency for your build pipeline's results and runtime."
  :url "https://github.com/cburgmer/buildviz"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring/ring-json "0.4.0"]
                 [ring-middleware-accept "2.0.3"]
                 [compojure "1.6.1"]
                 [metosin/scjsv "0.5.0"]
                 [clj-http "3.9.1"]
                 [clj-time "0.15.1"]
                 [cheshire "5.8.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "0.4.1"]
                 [intervox/clj-progress "0.2.1"]
                 [uritemplate-clj "1.2.1"]
                 [wharf "0.2.0-20141115.032457-2"]]
  :plugins [[lein-ring "0.12.5"]
            [lein-npm "0.6.2"]]
  :npm {:dependencies [[d3 "3.5.5"]
                       [moment "2.22.2"]
                       [moment-duration-format "2.2.2"]]
        :root "resources/public"}
  :ring {:handler buildviz.main/app
         :init buildviz.main/help}
  :aot [buildviz.go.sync
        buildviz.jenkins.sync
        buildviz.teamcity.sync
        buildviz.data.junit-xml]
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]
                                  [clj-http-fake "1.0.3"]]}
             :test {:resource-paths ["test/resources"]}}
  :jvm-opts ["--illegal-access=deny"]) ; https://clojure.org/guides/faq#illegal_access
