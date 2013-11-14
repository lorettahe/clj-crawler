(ns new-clj-crawler.crawler
  "Tool used to crawl web pages with an aritrary handler."
  (:require [clojure.string :as s]
            [clojure.tools.logging :refer [debug error info trace warn]]
            [clj-http.client :as http]
            [slingshot.slingshot :refer [get-thrown-object try+]]
            [cemerick.url :refer [url]]
            [clj-http.util :as util]
            [new-clj-crawler.robots :as robots])
  (:import (java.net URL)
           (java.util LinkedList))
  (:use [clojure.tools.cli :only [cli]]
        [new-clj-crawler.config-utils :only [parse-config]]
        [new-clj-crawler.page-downloader :only [download-page valid-url?]]
        [new-clj-crawler.hash-utils :only [seen-page?]]
        [new-clj-crawler.url-extractor :only [extract-all]]))

(defn- enqueue
  "Internal function to enqueue a url as a map with :url and :count."
  [config url]
  (.add (-> config :state :url-queue)
        {:url url :count (-> config :state :url-count)})
  config)

(defn enqueue-url
  "Enqueue the url assuming the url-count is below the limit and we haven't seen
  this url before."
  [config url]
  (if (get (-> config :state :seen-urls) url)
    (update-in config [:state :seen-urls url] inc)
    (when (or (neg? (:page-limit config))
              (< (-> config :state :url-count) (:page-limit config)))
      (when-let [url-info (valid-url? url)]
        (let [config (update-in config [:state :seen-urls] assoc url 1)
              include-regexes (:inclusion-regexes config)
              exclude-regexes (:exclusion-regexes config)]
          (if (and (or (nil? include-regexes) (not-every? nil? (map #(re-matches % url) include-regexes)))
                       (or (nil? exclude-regexes) (every? nil? (map #(re-matches % url) exclude-regexes))))
            (enqueue config url)
            config))))))

(defn enqueue-urls
  "Enqueue a collection of urls for work"
  [config urls]
  (reduce #(or (enqueue-url %1 %2) %1) config urls))

(defn http-opts 
  [encoding]
  {:socket-timeout 10000
   :conn-timeout 10000
   :insecure? true
   :throw-entire-message? false
   :decode-body-headers true
   :as (or encoding :auto)})

(defn- crawl-page
  "Internal crawling function that fetches a page, enqueues url found on that
  page and calls the handler with the page body."
  [config url-map]
  (try+
   (let [url (:url url-map)
         score (:count url-map)
         before-download (System/currentTimeMillis)
         body (:body (http/get url (http-opts (:encoding config))))
         after-download (System/currentTimeMillis)]
     (if-let [new-config (seen-page? config body)]
       (let [enqueued-config (enqueue-urls new-config (extract-all url body))]
         (try
           (download-page (assoc url-map :body body :dest (:dest config)))
           (update-in enqueued-config [:state :url-count] inc)
         (catch Exception e
           (error e "Exception executing handler"))))
       config))
   (catch java.net.SocketTimeoutException e
     (println "connection timed out to" (:url url-map)))
   (catch org.apache.http.conn.ConnectTimeoutException e
     (println "connection timed out to" (:url url-map)))
   (catch java.net.UnknownHostException e
     (println "unknown host" (:url url-map) "skipping."))
   (catch org.apache.http.conn.HttpHostConnectException e
     (println "unable to connect to" (:url url-map) "skipping"))
   (catch map? m
     (println "unknown exception retrieving" (:url url-map) "skipping.")
     (println (dissoc m :body) "caught"))
   (catch java.net.URISyntaxException e
     (println "URI problem for url " (:url url-map) ". Skipping."))
   (catch Object e
     (println e "!!!"))))

(defn- worker-fn
  "Generate a worker function for a config object."
  [config]
  (fn worker-fn* []
    (loop [config (enqueue-url config (:url config))]
      (let [tid (.getId (Thread/currentThread))
            state (:state config)
            _ (if (nil? (:page-limit config)) (println "Strange config: " config))
            limit-reached (and (pos? (:page-limit config))
                               (= (:url-count state) (:page-limit config)))
            no-more-pages (= 0 (.size (-> config :state :url-queue)))]
        (if limit-reached
          (println (str "page limit reached: (" (:url-count state)
                      "/" (:page-limit config) "), terminating myself")))
        (if no-more-pages
          (println (str "No more pages found. Terminating myself.")))
        (if (and (not limit-reached) (not no-more-pages))
          (when-let [url-map (.poll (-> config :state :url-queue))]
            (let [_ (println "Checking URL " (:url url-map))
                  should-crawl? (robots/crawlable? (:url url-map) (-> config :state :robots))
                  crawler-delay (:crawler-delay config)
                  before-crawl (System/currentTimeMillis)
                  new-config (if should-crawl? 
                               (or (crawl-page config url-map) config)
                               config)
                  after-crawl (System/currentTimeMillis)
                  _ (if should-crawl? (Thread/sleep (if (= crawler-delay :auto) (* 1.5 (- after-crawl before-crawl)) (* 1000 crawler-delay))))]
              (recur new-config))))))))

(defn- start-worker
  [config]
  (let [directives (robots/fetch-robots (:url config))
        config (merge {:page-limit -1
                       :crawler-delay (if (= directives :not-found) :auto (or (:crawler-delay directives) :auto))
                       :inclusion-regexes #{}
                       :exclusion-regexes #{}
                       :state {:url-queue (LinkedList.)
                               :url-count 0
                               :seen-urls {}
                               :robots directives
                               :seen-hashes #{}}}
                      config)
        config (update-in config [:inclusion-regexes] conj (re-pattern (str (:url config) ".*")))
        config (update-in config [:exclusion-regexes] conj #".*\.jpg" #".*\.png" #".*\.gif" #".*\.js" #".*\.css")
        w-thread (Thread. (worker-fn config))
        _ (.setName w-thread (str "crawler-worker-" (.getName w-thread)))
        w-tid (.getId w-thread)]
    (println "Starting thread:" w-thread w-tid " for URL " (:url config))
    (.start w-thread)))

(defn crawl
  [config]
  (http/with-connection-pool {:timeout 5
                              :threads (count config)
                              :insecure? true}
    (doseq [single-config config]
      (start-worker single-config))))