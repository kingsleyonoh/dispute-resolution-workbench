(ns drw.security.hmac
  (:require [clojure.string :as str])
  (:import [java.security MessageDigest]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(def algorithm "HmacSHA256")
(def prefix "sha256=")

(defn- bytes->hex [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))

(defn signature [secret body]
  (let [mac (Mac/getInstance algorithm)]
    (.init mac (SecretKeySpec. (.getBytes secret "UTF-8") algorithm))
    (str prefix (bytes->hex (.doFinal mac (.getBytes body "UTF-8"))))))

(defn signed-message [timestamp delivery-id body]
  (str timestamp "." delivery-id "." body))

(defn valid-signature? [secret body presented]
  (when (and (not (str/blank? secret))
             (not (str/blank? presented))
             (str/starts-with? presented prefix))
    (MessageDigest/isEqual (.getBytes (signature secret body) "UTF-8")
                           (.getBytes presented "UTF-8"))))
