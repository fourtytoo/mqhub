(ns mqhub.mqtt
  (:require [taoensso.timbre :as log]
            [clojurewerkz.machine-head.client :as mh]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]
            [mqhub.conf :refer :all]))


(defn connect [& [opts]]
  (let [conn (mh/connect (conf :mqtt :broker)
                         (merge {:client-id (conf :mqtt :client-id)
                                 :auto-reconnect true}
                                opts))]
    (mh/publish conn "hello" "mqhub connected")
    conn))

(def connection (delay (connect)))

(defn subscribe [topic f]
  (mh/subscribe @connection topic
                (fn [topic metadata payload]
                  (try
                    (let [payload (String. payload "UTF-8")]
                      (log/debug "received" topic ":" payload)
                      (f topic payload))
                    (catch Exception e
                      (log/error e "Error in topic listener.")
                      #_(System/exit 2))))))

(defn parse-topic [topic parts]
    (->> (s/split topic #"/" (count parts))
         (zipmap parts)))
