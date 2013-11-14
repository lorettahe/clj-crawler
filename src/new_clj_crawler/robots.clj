(ns new-clj-crawler.robots
  (:require [cemerick.url    :as url]
            [clj-http.client :as client]
            [clj-robots.core :as robots]))

(defn http-opts 
  [encoding]
  {:socket-timeout 10000
   :conn-timeout 10000
   :insecure? true
   :throw-entire-message? false
   :decode-body-headers true
   :as (or encoding :auto)})

(defn fetch-robots
  [link]
  (try 
    (let [robots-url (str "http://" (-> link (url/url) :host) "/robots.txt")
          robots-content-map (client/get robots-url http-opts)
          valid-robots? (and (= 200 (:status robots-content-map)) (= (:path robots-content-map) "/robots.txt"))]
      (if valid-robots?
        (robots/parse (:body robots-content-map))
        :not-found))
  (catch Exception e :not-found)))

(defn crawlable?
  [link directives]
  (if (= directives :not-found)
    true
    (robots/crawlable? directives link)))