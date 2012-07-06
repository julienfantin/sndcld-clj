(ns sndcld-clj.recommendations
  (:require [sndcld-clj.api :as api]
            [sndcld-clj.resources :as resources]
            [sndcld-clj.metrics :as metrics]
            [clojure.algo.monads :as m])
  (:use [sndcld-clj.cps]))

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

;; Recommendations
(declare get-user-favorites
         get-track-favoriters
         get-favoriters-favorites
         filter-non-favorited-tracks
         sort-users-by-syimilarity
         prune-irrelevant-recommendations)

(defn user-similarity [x y]
  (metrics/similarity (get-user-favorites x) (get-user-favorites y)))

(defn track-similarity [x y]
  (metrics/similarity (get-track-favoriters x) (get-track-favoriters y)))

(defn sort-users-by-similarity [user others]
  "Return a collection consisting of others sorted by their similarity
  to user (descending)."
  (sort-by
   #(user-similarity user %)
   >
   others))

(defn signal-result [state]
  state)

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

(defn make-recommendations [user track]
  (for [user-favorite (get-user-favorites user)
        other-favoriter (get-track-favoriters user track)]
    (let [favoriter-favorites (future (get-user-favorites other-favoriter))]
      (future (signal-result
               {:user other-favoriter
                :score (future (metrics/similarity (get-user-favorites user) @favoriter-favorites))
                :tracks (future (filter-non-favorited-tracks user @favoriter-favorites))})))))

(defn- get-user-favorites-cached [user]
  (get @favorites-cache user))

(defn- cache-user-favorites [user favorites]
  (swap! favorites-cache assoc user favorites))

(defn get-user-favorites [user]
  (if-let [favs (get-user-favorites-cached user)]
    favs
    (let [response (api/request (resources/subresource user))]
      (cache-user-favorites user response))))

(defn- get-track-favoriters-cached [track]
  (get @favoriters-cache track))

(defn- cache-track-favoriters [track favoriters]
  (swap! favoriters-cache assoc track favoriters))

(defn get-track-favoriters [track]
  (if-let [favrs (get-track-favoriters-cached track)]
    favrs
    (let [response (api/request (resources/subresource track))]
      (cache-track-favoriters track response))))

(defn remove-favorited-tracks [user tracks]
  "Filter out any track in tracks that's already part of user's favorites."
  (let [user-favorites (get-user-favorites user)]
    (filter (fn [track]
              (not (some #(= % track) user-favorites)))
            tracks)))

(defn cps-get-user-favorites [user callback]
  (if-let [favs (get-user-favorites-cached user)]
    favs
    (api/async-request (resources/subresource user) callback)))

(defn cps-get-track-favoriters [track callback]
  (if-let [favrs (get-track-favoriters-cached track)]
    favrs
    (api/async-request (resources/subresource track) callback)))

(m/with-monad m/sequence-m
  (def mk-seq-cont m/m-result))

(defn recs [user track]
  (m/with-monad (m/maybe-t (m/sequence-t m/cont-m))
    (m/domonad [fav (mk-cps (partial cps-get-user-favorites user))
                favr (mk-cps (partial cps-get-track-favoriters fav))
                ;;favr-fav (mk-cps (partial cps-get-user-favorites favr))
                favr-favs (mk-seq-cont (mk-cps (partial cps-get-user-favorites favr)))]
               (prn (count favr-favs))
               )))
