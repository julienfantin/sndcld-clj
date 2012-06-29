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

;; Queueing
(def queue (LinkedBlockingQueue.))

(defn queue-recommendations-request [user track]
  (.add queue [user track]))

;; Agents
(def ^:const agents-count 8)

(def agents (set (repeatedly agents-count #(agent {:queue queue}))))

(defn paused? [agent] (::paused (meta agent)))

(defn run
  ([] (doseq [a agents] (run a)))
  ([agent]))

(defn pause
  [] (doseq [a agents] (pause a))
  [agent] (alter-meta! agent assoc ::paused true))

(defn resume
  [] (doseq [a agents] (resume a))
  [agent] (alter-meta! agent assoc ::paused false)
  (run agent))

;; (defn run
;;   ([] (doseq [a agents] (run a)))
;;   ([a]
;;      (when (agents a)
;;        (send a (fn [{transition ::t :as state}]
;;                  (when-not (paused? *agent*)
;;                    (let [dispatch-fn (if (-> transition meta ::blocking)
;;                                        send-off
;;                                        send)] (dispatch-fn *agent* transition)))
;;                  state)))))

;; Recommendations
(declare get-user-favorites
         get-track-favoriters
         get-favoriters-favorites
         sort-users-by-similarity
         prune-irrelevant-recommendations)

(defn- filter-out-user [user users]
  (filter #(not (= (:id user) (:id %))) users))

(defn ^::blocking recommendations [user track]
  (try
    (let [user-favorites (get-user-favorites user)
          track-favoriters (get-track-favoriters user track)
          favoriters-favorites (get-favoriters-favorites track-favoriters)
          sorted-favoriters (sort-users-by-similarity user track-favoriters)
          top-favoriter (first sorted-favoriters)]
      (take 10 (filter-non-favorited-tracks user (get-user-favorites top-favoriter))))))

(defn ^::blocking get-user-favorites [user]
  (if-let [favs (@favorites user)]
    favs
    (let [response (resources/request (resources/subresource user))]
      (swap! favorites assoc user response))))

(defn ^::blocking get-track-favoriters [user track]
  (filter-out-user user (if-let [favrs (@favoriters track)]
                          favrs
                          (let [response (resources/request (resources/subresource track))]
                            (swap! favoriters assoc track response)))))

(defn ^::blocking get-favoriters-favorites [users]
  (reduce
   (fn [results user]
     (into results (get-user-favorites user)))
   []
   users))

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
