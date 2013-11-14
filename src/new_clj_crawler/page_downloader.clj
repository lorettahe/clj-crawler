(ns new-clj-crawler.page-downloader
  (:require [clojure.java.io :refer [file]]
            [cemerick.url :refer [url]]
            [clojure.tools.logging :refer [debug error info trace warn]])
  (:import (java.io ByteArrayInputStream)
           (org.apache.tika.sax BodyContentHandler)
           (org.apache.tika.metadata Metadata)
           (org.apache.tika.parser ParseContext)
           (org.apache.tika.parser.html HtmlParser)))

(defn valid-url?
  "Test whether a URL is valid, returning a map of information about it if
  valid, nil otherwise."
  [url-str]
  (try
    (url url-str)
    (catch Exception _ nil)))

(defn html->str
  "Convert HTML to plain text using Apache Tika"
  [body]
  (when body
    (let [bais (ByteArrayInputStream. (.getBytes body))
          handler (BodyContentHandler. Integer/MAX_VALUE)
          metadata (Metadata.)
          parser (HtmlParser.)]
      (.parse parser bais handler metadata (ParseContext.))
      (.toString handler))))

(defn download-page
  [config]
  (let [dest-file (file (or (:dest config) (str (.replaceAll (:host (valid-url? (:url config))) "\\." "_") ".txt")))]
    (when-not (.exists dest-file)
      (spit dest-file ""))
    (spit dest-file (.replaceAll (str (html->str (:body config))) "\\n\\s*\\n" "") :append true)))