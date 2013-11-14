(ns new-clj-crawler.core
  (:require [new-clj-crawler.crawler :as crawler]
            [new-clj-crawler.config-utils :as config-utils])
  (:use [clojure.tools.cli :only [cli]]))

(defn -main
  [& args]
  "This crawler allows one to crawl a set of URLs at the same time.
   For a sample of the config file, please see urls.json at the root directory of the source repository."
  (-> args
    (cli ["-c" "--config" "Json file containing URLs to be crawled and corresponding configurations" :default "config.json"])
    first
    :config
    config-utils/parse-config
    crawler/crawl))