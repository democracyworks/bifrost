(defproject democracyworks/bifrost "0.1.4"
  :description "Library for writing HTTP API gateways with Pedestal & core.async"
  :url "https://github.com/democracyworks/bifrost"
  :license {:name "Mozilla Public License"
            :url "http://www.mozilla.org/MPL/2.0/"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [io.pedestal/pedestal.service "0.4.0"]]
  :deploy-repositories {"releases" :clojars})
