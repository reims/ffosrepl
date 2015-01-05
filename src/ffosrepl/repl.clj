(ns ffosrepl.repl
  (:require [ffosrepl.proto :as proto]
            [cljs.repl]
            [cljs.closure :as cljsc]
            [cljs.compiler :as cmp]
            [cljs.js-deps :as js-deps]
            [cljs.env :as env]
            [clojure.set :as set]))

(declare transitive-deps
         ffos-setup-env
         ffos-evaluate-js
         ffos-load-js
         ffos-tear-down)

(defrecord FFOSReplEnv []
  cljs.repl/IJavaScriptEnv
  (-setup [this] (ffos-setup-env this))
  (-evaluate [this _ _ js] (ffos-evaluate-js this js))
  (-load [this nses url] (ffos-load-js this nses url))
  (-tear-down [this] (ffos-tear-down this)))

(defn repl-env [app-name & {:keys [host port] :as opts}]
  (let [compiler-env (env/default-compiler-env opts)
        opts (merge (FFOSReplEnv.)
                    {:host "127.0.0.1"
                     :port 6000
                     :app-name app-name
                     ::env/compiler compiler-env
                     :src "src/"
                     :loaded-libs (atom #{})
                     :ffos-proto (atom {})}
                    opts)]
    (swap! compiler-env assoc :js-dependency-index (js-deps/js-dependency-index opts))
    (env/with-compiler-env (::env/compiler opts)
      (reset! (:loaded-libs opts)
              (set/union (into #{} (map str (:preloaded-libs opts)))
                         (transitive-deps ["ffosrepl.repl"] opts))))
    opts))


(defn- ffos-setup-env [{:keys [host port app-name ffos-proto src] :as opts}]
  (let [connection (proto/connect! host port)
        webapps-actor (proto/get-webapps-actor! connection)
        manifest-url (proto/get-manifest-url! connection webapps-actor app-name)]
    (proto/ensure-app-is-running! connection webapps-actor manifest-url)
    (cljs.repl/analyze-source src)
    (let [console-actor (proto/get-console-actor! connection webapps-actor manifest-url)]
      (swap! ffos-proto assoc
             :connection connection
             :webapps-actor webapps-actor
             :manifest-url manifest-url
             :console-actor console-actor))
    (env/with-compiler-env (::env/compiler opts)
      (ffos-evaluate-js opts (cljsc/compile-form-seq '[(ns cljs.user)])))))

(defn- ffos-evaluate-js [opts js]
  (let [{:keys [connection console-actor]} @(:ffos-proto opts)
        res (proto/eval-js! connection console-actor js)]
    (if (:exception res)
      {:status :exception
       :value (:exceptionMessage res)}
      {:status :success
       :value (:result res)})))

(defn- ffos-load-js [opts nses url]
  (when-let [not-loaded (seq (remove @(:loaded-libs opts) nses))]
    (ffos-evaluate-js opts (slurp url))
    (swap! (:loaded-libs opts) #(apply conj % not-loaded))))

(defn- ffos-tear-down [opts]
  (proto/close! (:connection @(:ffos-proto opts))))

(defn- transitive-deps
  "Returns a flattened set of all transitive namespaces required and
  provided by the given sequence of namespaces

  stolen from weasel repl source"
  [nses opts]
  (let [collect-deps #(flatten (mapcat (juxt :provides :requires) %))
        cljs-deps (->> nses (cljsc/cljs-dependencies opts) collect-deps)
        js-deps (->> cljs-deps (cljsc/js-dependencies opts) collect-deps)]
    (disj (into #{} (concat js-deps cljs-deps)) nil)))
