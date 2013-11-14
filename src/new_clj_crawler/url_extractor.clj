(ns new-clj-crawler.url-extractor
  (:require [clojure.set :as set]
            [cemerick.url :refer [url]])
  (:import (org.apache.commons.lang StringEscapeUtils)))

(def url-regex #"https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]")

(defn extract-all
  "Dumb URL extraction based on regular expressions. Extracts relative URLs."
  [original-url body]
  (when-let [body (StringEscapeUtils/unescapeHtml (str body))]
    (let [candidates1 (->> (re-seq #"href=\"([^\"]+)\"" body)
                           (map second)
                           (remove #(or (= % "/")
                                        (.startsWith % "#")))
                           set)
          candidates2 (->> (re-seq #"href='([^']+)'" body)
                           (map second)
                           (remove #(or (= % "/")
                                        (.startsWith % "#")))
                           set)
          candidates3 (re-seq url-regex body)
          all-candidates (set (concat candidates1 candidates2 candidates3))
          double-slash (set (filter #(.startsWith % "//") all-candidates))
          not-double-slash (set/difference all-candidates double-slash)
          all-candidates (set (concat (map #(str "http:" %) double-slash) not-double-slash))
          fq (set (filter #(.startsWith % "http") all-candidates))
          ufq (set/difference all-candidates fq)
          fq-ufq (map #(str (url original-url %)) ufq)
          all (set (concat fq fq-ufq))]
      all)))