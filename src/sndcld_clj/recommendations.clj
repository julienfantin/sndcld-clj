(ns sndcld-clj.recommendations
  (:require [sndcld-clj.api :as api]
            [sndcld-clj.resources :as resources]
            [sndcld-clj.metrics :as metrics]
            [sndcld-clj.persistence :as persist]
            [clojure.algo.monads :as m])
  (:use [sndcld-clj.cps]))

;; Recommendations
(defn user-similarity [x y]
  (metrics/similarity (persist/cached-subresources x) (persist/cached-subresources y)))

(defn track-similarity [x y]
  (metrics/similarity (persist/cached-subresources x) (persist/cached-subresources y)))

(defn sort-users-by-similarity [user others]
  "Return a collection consisting of `others` sorted by their similarity
  to `user` (descending)."
  (sort-by
   #(user-similarity user %)
   >
   others))

(defn filter-out-user [user users]
  "Return a collection of `users` where `user` is filtered-out."
  (filter #(not (= (:id user) (:id %))) users))

(defn remove-favorited-tracks [user-favorites tracks]
  "Filter out any track in `tracks` that's already part of
`user-favorites`."
  (filter (fn [track]
            (not (some #(= % track) user-favorites)))
          tracks))

(defn get-user-favorites [user callback]
  "CPS style getter which will either apply a cached version of the
result to the callback, or queue a request which will trigger the
callback on completion."
  (if-let [favs (persist/cached-subresources user)]
    (callback favs)
    (api/async-request (resources/subresource user) callback)))

(defn get-track-favoriters [track callback]
  "CPS style getter which will either apply a cached version of the
result to the callback, or queue a request which will trigger the
callback on completion."
  (if-let [favrs (persist/cached-subresources track)]
    (callback favrs)
    (api/async-request (resources/subresource track) callback)))

(defn recs [user track]
  (m/run-cont
   (m/with-monad (m/sequence-t (m/maybe-t m/cont-m))
     (m/domonad
      [fav (mk-cps (partial get-user-favorites user))
       favr (mk-cps (partial get-track-favoriters fav))]
      (m/run-cont
       (m/with-monad (m/maybe-t m/cont-m)
         (m/domonad
          [favr-favs (mk-cps (partial get-user-favorites favr))]
          (do
            (let [score (user-similarity user favr)]
              (persist/put-similarity user favr score)
              (prn
               (str
                "Similarity with " (:id favr) " : " score)))))))))))
