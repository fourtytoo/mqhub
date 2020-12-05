(ns mqhub.core
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [unilog.config  :refer [start-logging!]]
            [clojurewerkz.machine-head.client :as mh]
            [cprop.core :as cprop]
            [cheshire.core :as json]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]
            [postal.core :as post])
  (:gen-class))


(defn home-conf []
  (io/file (System/getProperty "user.home") ".mqhub"))

(defn load-configuration []
  (let [c (home-conf)]
    (cprop/load-config :file (when (.exists c)
                               c))))

(def ^:dynamic config (load-configuration))

(def devices (atom {}))

(defn conf [& path]
  (get-in config path))

(defn parse-topic [topic]
  (->> (s/split topic #"/" (count (conf :mqtt :topic-structure)))
       (zipmap (conf :mqtt :topic-structure))))

(defn telemetry? [topic]
  (and (= "tele" (:type topic))
       (= "SENSOR" (:what topic))))

(defn parse-time [s]
  (java.time.LocalDateTime/parse s))

(defn parse-data [data]
  (-> (json/parse-string data csk/->kebab-case-keyword)
      (update :time parse-time)
      (update-in [:energy :total-start-time] parse-time)))

(defn telemetry-printer [topic payload]
  (prn topic (parse-data payload)))

(defn assoc-device [topic k v]
  (swap! devices update topic assoc k v))

(defn dissoc-device [topic k]
  (swap! devices update topic dissoc k))

(defn update-device [topic k f & args]
  (apply swap! devices update topic update k f args))

(defmulti notify (fn [action topic] (:type action)))

(defmethod notify :mail
  [action topic]
  ;; allow the configuration file to override the host, the from, the
  ;; body and the subject
  (post/send-message (merge {:host "localhost"}
                            (conf :smtp :server)
                            (:server action))
                     (merge {:from "mqhub@localhost"
                             :subject (str "Notification from mqhub")
                             :body (str topic " triggered a notification for you.")}
                            (conf :smtp :message)
                            (:message action))))

(def default-avg-samples 5)

(defn cma
  "Calculate the Cumulative Moving Average of `x`."
  [samples old-avg new-value]
  (let [old-avg (or old-avg new-value)]
    (float                    ; avoid accumulating huge ratios
     (+ old-avg
        (/ (- new-value old-avg)
           (inc (or samples default-avg-samples)))))))

(defn make-telemetry-listener
  [config]
  (fn [topic payload]
    (let [{:keys [telemetry threshold hysteresis trigger action]} config
          data (parse-data payload)
          state (get-in @devices [topic :state] :off)
          avg-telemetry (cma (get config :avg-samples) (get-in @devices [topic :avg]) (get-in data telemetry))
          on-threshold (* threshold (+ 1 hysteresis))
          off-threshold (* threshold (- 1 hysteresis))
          current-state (cond (< avg-telemetry off-threshold) :off
                              (> avg-telemetry on-threshold) :on
                              :else state)]
      (assoc-device topic :avg avg-telemetry)
      (log/debug "average telemetry for" topic ":"
                 avg-telemetry (str "(" current-state ")"))
      (when (not= state current-state)
        (assoc-device topic :state current-state)
        (log/debug "switching state of" topic ":" state "->" current-state)
        (when (or (and (= current-state :on) (= trigger :off-to-on))
                  (and (= current-state :off) (= trigger :on-to-off)))
          (notify action topic)
          (log/info "topic" topic
                    "triggered action" (:type action)))))))

(defn subscribe [conn topic f]
  (mh/subscribe conn topic
                (fn [topic metadata payload]
                  (try
                    (let [payload (String. payload "UTF-8")]
                      (log/debug "received" topic ":" payload)
                      (f topic payload))
                    (catch Exception e
                      (log/error e "Error in topic listener.")
                      (System/exit 2))))))

(defn start-monitor [make-listener]
  (log/info "Monitoring:" (s/join ", " (keys (conf :devices))))
  (let [conn (mh/connect (conf :mqtt :broker) {:client-id (conf :mqtt :client-id)})]
    (doseq [[topic configuration] (conf :devices)]
      (subscribe conn {topic 0} (make-listener configuration)))
    (mh/publish conn "hello" "mqhub connected")))

#_(log/debug "foo")

(defn -main [& args]
  (start-logging! (conf :logging))
  (start-monitor make-telemetry-listener)
  (println "Monitor started.  Type Ctrl-C to exit.")
  (while true
    (Thread/sleep 1000)))
