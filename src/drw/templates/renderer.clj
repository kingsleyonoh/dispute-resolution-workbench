(ns drw.templates.renderer
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [selmer.parser :as selmer]
            [drw.templates.strict-fetch :as strict])
  (:import [java.security MessageDigest]))

(def dispute-audit-template "templates/pdf/dispute_audit.html")

(defn render-file [template context]
  (selmer/render-file template context strict/selmer-options))

(defn- hex [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))

(defn sha256 [bytes]
  (hex (.digest (MessageDigest/getInstance "SHA-256") bytes)))

(defn- shell-render! [cfg html]
  (let [html-file (java.io.File/createTempFile "drw-report-" ".html")
        pdf-file (java.io.File/createTempFile "drw-report-" ".pdf")
        binary (or (:wkhtmltopdf-path cfg) "wkhtmltopdf")]
    (spit html-file html)
    (let [{:keys [exit err]} (shell/sh binary
                                       (.getAbsolutePath html-file)
                                       (.getAbsolutePath pdf-file))]
      (when-not (zero? exit)
        (throw (ex-info "wkhtmltopdf failed"
                        {:type :report/pdf-render-failed
                         :exit exit
                         :error err})))
      (java.nio.file.Files/readAllBytes (.toPath pdf-file)))))

(defn render-pdf-bytes! [cfg request]
  (if-let [f (:pdf-render-fn cfg)]
    (f request)
    (shell-render! cfg (:html request))))

(defn store-pdf! [cfg report-id bytes]
  (let [dir (io/file (or (:report-storage-dir cfg) "target/reports"))
        file (io/file dir (str report-id ".pdf"))]
    (io/make-parents file)
    (with-open [out (io/output-stream file)]
      (.write out bytes))
    {:storage-path (.getPath file)
     :content-sha256 (sha256 bytes)}))
