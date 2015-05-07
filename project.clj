(defproject buildviz "0.1.0-SNAPSHOT"
  :description "Transparency for your build pipeline's results and runtime."
  :url "http://example.com/FIXME"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :main ^:skip-aot buildviz.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
