(defproject sndcld-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [com.soundcloud/java-api-wrapper "1.1.1"]
                 [clj-http               "0.4.1" :exclusions [org.clojure/clojure]]
                 [clj-http-fake          "0.3.0"]
                 [org.clojure/data.json  "0.1.2"]])
