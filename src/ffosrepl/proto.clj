(ns ffosrepl.proto
  (:require [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [clojure.string :refer [split join]]
            [clojure.data.json :as json]
            [byte-streams :refer [to-string to-byte-array]]))

(defn- packet->json [packet]
  (join ":" (rest (split packet #":"))))

(defn- packet-length [packet]
  (bigint (first (split packet #":"))))

(defn- packet->edn [packet]
  (-> packet
      packet->json
      (json/read-str :key-fn keyword)))

(defn- edn->packet [data]
  (let [json (json/write-str data)
        len (count json)]
    (str len ":" json)))

(defn- receive! [connection]
  (loop [packet ""]
    (if-let [byte-data (deref (s/take! connection) 5000 nil)]
      (let [data (to-string byte-data)
            new-packet (str packet data)
            length (packet-length new-packet)
            json (packet->json new-packet)]
        (if (> length (count (to-byte-array json)))
          (recur new-packet)
          (packet->edn new-packet)))
      (throw (Exception. "timeout while waiting for message from client.")))))

(defn send-sync! [connection data]
  (let [packet (edn->packet data)
        success (deref (s/put! connection packet) 5000 nil)]
    (if success
      (receive! connection)
      (throw (Exception. "timeout or error while sending to client.")))))

(defn connect! [host port]
  (let [client (tcp/client {:host host :port port})]
    (if-let [connection (deref client 5000 nil)]
      (let [reply (receive! connection)]
        (when (:applicationType reply)
          connection))
      (throw (Exception. "timeout while connecting to client.")))))

(defn close! [connection]
  (s/close! connection))

(defn get-webapps-actor! [connection]
  (let [reply (send-sync! connection {:to "root" :type "listTabs"})]
    (:webappsActor reply)))

(defn get-manifest-url! [connection webapps-actor app-name]
  (let [reply (send-sync! connection {:to webapps-actor :type "getAll"})]
    (->> reply
         :apps
         (filter #(= (:name %) app-name))
         first
         :manifestURL)))

(defn is-app-running! [connection webapps-actor manifest-url]
  (let [reply (send-sync! connection {:to webapps-actor :type "listRunningApps"})]
    (some #{manifest-url} (:apps reply))))

(defn launch-app! [connection webapps-actor manifest-url]
  (send-sync! connection {:to webapps-actor :type "launch" :manifestURL manifest-url}))

(defn ensure-app-is-running! [connection webapps-actor manifest-url]
  (when-not (is-app-running! connection webapps-actor manifest-url)
    (launch-app! connection webapps-actor manifest-url)))

(defn get-console-actor! [connection webapps-actor manifest-url]
  (let [reply (send-sync! connection {:to webapps-actor :type "getAppActor" :manifestURL manifest-url})]
    (get-in reply [:actor :consoleActor])))

(defn eval-js! [connection console-actor js]
  (let [reply (send-sync! connection {:to console-actor :type "evaluateJS" :text js})]
    reply))
