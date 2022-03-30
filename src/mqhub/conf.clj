(ns mqhub.conf
  (:require [clojure.java.io :as io]
            [cprop.core :as cprop]))

(defn- home-conf []
  (io/file (System/getProperty "user.home") ".mqhub"))

(defn- load-configuration []
  (let [c (home-conf)]
    (cprop/load-config :file (when (.exists c)
                               c))))

(def ^:dynamic config (load-configuration))

(defn conf [& path]
  (get-in config path))

