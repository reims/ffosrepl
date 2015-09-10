(defproject ffosrepl "0.1.0"
  :description "a ClojureScript REPL environment for Firefox OS"
  :url "https://github.com/reims/ffosrepl"
  :license {:name "Unlicense"
            :url "http://unlicense.org/"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [aleph "0.4.0-alpha9"]
                 [byte-streams "0.2.0-alpha3"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/clojurescript "0.0-2371" :scope "provided"]
                 [org.clojure/google-closure-library "0.0-20140226-71326067" :scope "provided"]
                 [com.cemerick/piggieback "0.1.3"]]
  :source-paths ["src"])
