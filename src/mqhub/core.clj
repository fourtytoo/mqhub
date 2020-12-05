(ns mqhub.core
  (:require [clojurewerkz.machine-head.client :as mh]
            [cprop.core :as cprop]
            [cheshire.core :as json]
            [clojure.string :as s]
            [camel-snake-kebab.core :as csk]
            [postal.core :as post]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io])
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

(defn parse-data [topic data]
  (if (telemetry? topic)
    (-> (json/parse-string data csk/->kebab-case-keyword)
        (update :time parse-time)
        (update-in [:energy :total-start-time] parse-time))
    data))

(defn telemetry-printer [topic payload]
  (prn topic (parse-data topic payload)))

(defn assoc-device [place device k v]
  (swap! devices update-in [place device] assoc k v))

(defn dissoc-device [place device k]
  (swap! devices update-in [place device] dissoc k))

(defn update-device [place device k f & args]
  (apply swap! devices update-in [place device] update k f args))

(defmulti notify (fn [action place device] (:type action)))

(defmethod notify :mail
  [action place device]
  ;; allow the configuration file to override the host, the from, the
  ;; body and the subject
  (post/send-message (merge {:host "localhost"}
                            (conf :smtp :server)
                            (:server action))
                     (merge {:from "mqhub@localhost"
                             :subject (str "Notification from mqhub")
                             :body (str device " at " place " triggered a notification fo you.")}
                            (conf :smtp :message)
                            (:message action))))

(def default-avg-samples 5)

(defn cma [place device x]
  (let [old-avg (get-in @devices [place device :avg] 0)
        samples (conf :devices place device :avg-samples)
        avg (float                    ; avoid accumulating huge ratios
             (+ old-avg
                (/ (- x old-avg)
                   (inc (or samples default-avg-samples)))))]
    (assoc-device place device :avg avg)
    avg))

(defn telemetry-listener [topic payload]
  (let [{:keys [place device]} topic
        config (conf :devices place device)]
    (when (and (telemetry? topic)
               config)
      (let [{:keys [telemetry threshold hysteresis trigger action]} config
            data (parse-data topic payload)
            state (get-in @devices [place device :state] :off)
            avg-telemetry (cma place device (get-in data telemetry))
            on-threshold (* threshold (+ 1 hysteresis))
            off-threshold (* threshold (- 1 hysteresis))
            current-state (cond (< avg-telemetry off-threshold) :off
                                (> avg-telemetry on-threshold) :on
                                :else state)]
        (log/debug "average telemetry for" (str device "@" place ":")
                   avg-telemetry (str "(" current-state ")"))
        (when (not= state current-state)
          (assoc-device place device :state current-state)
          (log/debug "switching state of" (str device "@" place ":")
                     (str state "->" current-state))
          (when (or (and (= current-state :on) (= trigger :off-to-on))
                    (and (= current-state :off) (= trigger :on-to-off)))
            (notify action place device)
            (log/info "device" device "@" place
                      "triggered action" (:type action))))))))

(defn subscribe [conn topic f]
  (mh/subscribe conn topic
                (fn [topic metadata payload]
                  (try
                    (let [topic (parse-topic topic)
                          payload (String. payload "UTF-8")]
                      (log/debug "received" topic ":" payload)
                      (f topic payload))
                    (catch Exception e
                      (log/error e "Error in topic listener.")
                      (System/exit 2))))))

(defn start-monitor [listener]
  (let [conn (mh/connect (conf :mqtt :broker) {:client-id (conf :mqtt :client-id)})]
    (subscribe conn {(conf :mqtt :topic) 0} listener)
    (mh/publish conn "hello" "mqhub connected")))

(defn -main [& args]
  (start-monitor telemetry-listener)
  (println "Monitor started.  Type Ctrl-C to exit.")
  (while true
    (Thread/sleep 1000)))
