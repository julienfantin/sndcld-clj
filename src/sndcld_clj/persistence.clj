(ns sndcld-clj.persistence
  (:require [sndcld-clj.resources :as resources])
  ;; Major gotchas ensue
  (:import [sndcld_clj.resources User])
  (:import [sndcld_clj.resources Track]))

(def users-cache (atom {}))

(def tracks-cache (atom {}))

(def favoriters-cache
  "A map of tracks to a vector of users who favorited it."
  (atom {}))

(def favorites-cache
  "A map of users to a vector of tracsks they favorited."
  (atom {}))

(def user-similarity-cache
  (atom {}))

(defn clear-cache []
  (map #(swap! % empty) [favoriters-cache favorites-cache]))

(defprotocol Persistence
  (cache-resource [id])
  (cached-subresources [this])
  (cache-subresources [this subresources]))

(extend-protocol Persistence
  User
  (cache-resource [this]
    (swap! users-cache assoc (:id this) this))
  (cached-subresources [this]
    (get @favorites-cache this))
  (cache-subresources [this subresources]
    (swap! favorites-cache assoc this subresources))
  Track
  (cache-resource [this]
    (swap! tracks-cache assoc (:id this) this))
  (cached-subresources [this]
    (get @favoriters-cache this))
  (cache-subresources [this subresources]
    (swap! favoriters-cache assoc this subresources)))

;; Cache accessors
(defn similarity [user1 user2]
  (let [users #{user1 user2}]
    (get @user-similarity-cache users)))

(defn put-similarity [user1 user2 score]
  (when (not (= user1 user2))
    (let [users #{user1 user2}]
      (swap! user-similarity-cache assoc users score))))
