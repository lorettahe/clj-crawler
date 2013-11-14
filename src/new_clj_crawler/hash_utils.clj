(ns new-clj-crawler.hash-utils
  (:use [digest :only [md5]]))

(defn create-hash
  [s]
  (md5 s))

(defn seen-page?
  "This function checks if the hash of the html body content sent in it in the seen-hashes set.
   If it's there, it will return nil, otherwise it will return the new hash set with new hash included."
  [config body]
  (if body
    (let [new-hash (create-hash body)]
      (if-not ((-> config :state :seen-hashes) new-hash)
         (update-in config [:state :seen-hashes] conj new-hash)
         (println "Have seen the page before. Skipping.")))
    (println "No content in body. Skipping.")))