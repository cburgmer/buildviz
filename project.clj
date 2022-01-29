(defproject buildviz "0.15.0"
  :description "Transparency for your build pipeline's results and runtime."
  :url "https://github.com/cburgmer/buildviz"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.apache.logging.log4j/log4j-api "2.17.1"]
                 [org.apache.logging.log4j/log4j-core "2.17.1"]
                 [ring/ring-core "1.9.5"]
                 [ring/ring-jetty-adapter "1.9.5"]
                 [ring/ring-json "0.5.1"]
                 [ring-middleware-accept "2.0.3"]
                 [compojure "1.6.2"]
                 [luposlip/json-schema "0.3.3"]
                 [clj-http "3.12.3"]
                 [clj-time "0.15.2"]
                 [cheshire "5.10.2"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "1.0.206"]
                 [intervox/clj-progress "0.2.1"]
                 [uritemplate-clj "1.3.1"]
                 [wharf "0.2.0-20141115.032457-2"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler buildviz.main/app
         :init buildviz.main/help}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]
                                  [clj-http-fake "1.0.3"]]
                   :plugins [[lein-ancient "1.0.0-RC3"]
                             [lein-nvd "1.9.0"]]}
             :test {:resource-paths ["test/resources"]}}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory"
             "--illegal-access=deny"]) ; https://clojure.org/guides/faq#illegal_access
