(ns new-clj-crawler.config-utils
  (:require [clojure.data.json :as json]))

(defn- config-values-coercion
  [key value]
  (if (#{:inclusion-regexes :exclusion-regexes} key)
    (into #{} (map re-pattern value))
    value))

(defn parse-config
  [config-path]
  (-> config-path
    slurp
    (json/read-str :key-fn keyword :value-fn config-values-coercion)))