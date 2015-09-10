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
  (let [[receive-stream _] connection]
    (if-let [packet (deref (s/take! receive-stream) 5000 nil)]
      packet
      (throw (Exception. "timeout while waiting for message from client.")))))

(defn send-sync! [connection data]
  (let [[_ send-stream] connection
        packet (edn->packet data)
        success (deref (s/put! send-stream packet) 5000 nil)]
    (if success
      (receive! connection)
      (throw (Exception. "timeout or error while sending to client.")))))

(defn close! [connection]
  (s/close! (second connection)))

(defn- byte->packet-streams [connection]
  (let [packet (atom "")
        packet-stream (s/stream)
        send-stream (s/stream)]
    (s/consume (fn [byte-data]
                 (let [data (to-string byte-data)
                       new-packet (str @packet data)
                       length (packet-length new-packet)
                       json (packet->json new-packet)]
                   (if (> length (count (to-byte-array json)))
                     (reset! packet new-packet)
                     (do
                       (reset! packet "")
                       @(s/put! packet-stream (packet->edn new-packet))))))
               connection)
    (s/connect send-stream connection)
    [packet-stream send-stream]))

(defn- print-log-message [msg]
  (let [args (-> msg
                 :message
                 :arguments)]
    (when args
      (newline)
      (apply prn args))))

(defn- setup-connection! [tcp-connection]
  (let [[connection send-stream] (byte->packet-streams tcp-connection)
        reply-stream (s/stream)]
    (s/consume #(if (= (:type %) "consoleAPICall")
                  (print-log-message %)
                  (s/put! reply-stream %))
                   connection)
    [reply-stream send-stream]))

(defn connect! [host port]
  (let [client (tcp/client {:host host :port port})]
    (if-let [tcp-connection (deref client 5000 nil)]
      (let [connection (setup-connection! tcp-connection)
            reply (receive! connection)]
        (when (:applicationType reply)
          connection))
      (throw (Exception. "timeout while connecting to client.")))))

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

(defn start-console-listener! [connection console-actor]
  (send-sync! connection {:to console-actor :type "startListeners" :listeners ["ConsoleAPI"]}))

(defn eval-js! [connection console-actor js]
  (let [reply (send-sync! connection {:to console-actor :type "evaluateJS" :text js})]
    reply))
