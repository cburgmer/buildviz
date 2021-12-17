(defproject buildviz "0.14.3"
  :description "Transparency for your build pipeline's results and runtime."
  :url "https://github.com/cburgmer/buildviz"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.logging "1.2.2"]
                 [org.apache.logging.log4j/log4j-api "2.16.0"]
                 [org.apache.logging.log4j/log4j-core "2.16.0"]
                 [ring/ring-core "1.9.4"]
                 [ring/ring-jetty-adapter "1.9.4"]
                 [ring/ring-json "0.5.1"]
                 [ring-middleware-accept "2.0.3"]
                 [compojure "1.6.2"]
                 [luposlip/json-schema "0.3.2"]
                 [clj-http "3.12.3"]
                 [clj-time "0.15.2"]
                 [cheshire "5.10.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "1.0.206"]
                 [intervox/clj-progress "0.2.1"]
                 [uritemplate-clj "1.3.0"]
                 [wharf "0.2.0-20141115.032457-2"]]
  :plugins [[lein-ring "0.12.5"]
            [lein-npm "0.6.2"]]
  :npm {:dependencies [[d3 "3.5.5"]
                       [moment "2.22.2"]
                       [moment-duration-format "2.2.2"]]
        :devDependencies [[jshint "2.13.1"
                           prettier "2.5.1"]]
        :package {:scripts {:lint "jshint ./common ./graphs"
                            :prettier "prettier --write --tab-width 4 './common/*.js' './graphs/*.js'"}}
        :root "resources/public"}
  :ring {:handler buildviz.main/app
         :init buildviz.main/help}
  :aot [buildviz.go.sync
        buildviz.jenkins.sync
        buildviz.teamcity.sync
        buildviz.data.junit-xml]
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]
                                  [clj-http-fake "1.0.3"]]
                   :plugins [[lein-ancient "1.0.0-RC3"]]}
             :test {:resource-paths ["test/resources"]}}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory"
             "--illegal-access=deny"]) ; https://clojure.org/guides/faq#illegal_access
