(ns sndcld-clj.recommendations
  (:import java.util.concurrent.LinkedBlockingQueue)
  (:require [sndcld-clj.resources :as resources]
            [sndcld-clj.metrics :as metrics]))

(def favoriters
  "A map of tracks to a vector of users who favorited it."
  (atom {}))

(def favorites
  "A map of users to a vector of tracsks they favorited."
  (atom {}))

;; Recommendations
(declare get-user-favorites
         get-track-favoriters
         get-favoriters-favorites
         filter-non-favorited-tracks
         sort-users-by-similarity
         prune-irrelevant-recommendations)

(defn- filter-out-user [user users]
  (filter #(not (= (:id user) (:id %))) users))

(defn recommendations [user track]
  (future
    (let [user-favorites (future (get-user-favorites user))
          track-favoriters (future (get-track-favoriters user track))
          favoriters-favorites (future (get-favoriters-favorites @track-favoriters))
          sorted-favoriters (future (sort-users-by-similarity user @track-favoriters))
          top-favoriter (future (first @sorted-favoriters))]
      (take 10 (filter-non-favorited-tracks user (get-user-favorites @top-favoriter))))))

(defn get-user-favorites [user]
  (if-let [favs (@favorites user)]
    favs
    (let [response (resources/request (resources/subresource user))]
      (swap! favorites assoc user response))))

(defn get-track-favoriters [user track]
  (filter-out-user user (if-let [favrs (@favoriters track)]
                          favrs
                          (let [response (resources/request (resources/subresource track))]
                            (swap! favoriters assoc track response)))))

(defn get-favoriters-favorites [users]
  (let [futures (reduce
                 (fn [results user]
                   (into results (future (get-user-favorites user))))
                 []
                 users)]
    (do (map deref futures))))

(defn user-similarity [user1 user2]
  (metrics/similarity (get-user-favorites user1) (get-user-favorites user2)))

(defn sort-users-by-similarity [user others]
  "Return a collection consisting of others sorted by their similarity
  to user (descending)."
  (sort-by
   #(user-similarity user %)
   >
   others))

(defn filter-non-favorited-tracks [user tracks]
  "Filter out any track in tracks that's already part of user's favorites."
  (let [user-favorites (get-user-favorites user)]
    (filter (fn [track]
              (not (some #(= % track) user-favorites)))
            tracks)))
