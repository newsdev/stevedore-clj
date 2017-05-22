(defproject stevedore "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
      [org.clojure/clojure "1.8.0"]
      [clj-http "2.3.0"]
      [com.novemberain/pantomime "2.9.0"]
      [org.apache.commons/commons-compress "1.13"]

      [clojurewerkz/elastisch "3.0.0-beta2"]
      [cheshire "5.7.0"]
      [slingshot "0.12.2"]
      [amazonica "0.3.95"]
      [me.raynes/fs "1.4.6"]
      [org.clojure/tools.cli "0.3.5"]


  ]
  :resource-paths     ["resources"]
  :main ^:skip-aot stevedore.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
