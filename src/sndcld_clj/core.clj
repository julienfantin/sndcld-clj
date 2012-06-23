(ns sndcld-clj.core
  (:import com.soundcloud.api.ApiWrapper)
  (:import com.soundcloud.api.Env)
  (:import com.soundcloud.api.Token)
  (:import com.soundcloud.api.Request)
  (:import com.soundcloud.api.Http)
  (:import com.soundcloud.api.Endpoints)
  (:require [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.zip :as zip]))

(declare me user favorites favoriters)

;; Api wrapper
(def ^:const client-id "cab243f44bc34d57e99fb4e254d7ca3b")
(def ^:const client-secret "d1c880b44394cc3431ce4fbd0364aa26")

(defonce api
  (ApiWrapper. client-id client-secret nil nil Env/LIVE))

(defn login [user password]
  (.login api user password (into-array String [Token/SCOPE_NON_EXPIRING])))

(def ^:const api-limit 200)

(defn request [endpoint & args]
  "Issue a request for the given endpoint and optional args. Return a
native clj map of the response body on success."
  (let [request (Request/to endpoint (to-array args))
        response (.get api request)
        status (.getStatusCode (.getStatusLine response))
        body-string (Http/getString response)
        body (json/read-json body-string)]
    (if (= 200 status)
      body
      (println (str "Error " status " :\n" body)))))

;; Data model
(defrecord Track [id user favoriters])
(defrecord User [id tracks favorites])

;; Tree navigation
(defprotocol Node
  (branch? [node])
  (children [branch])
  (make-node [node children]))

(extend-protocol Node
  Object
  (branch? [x] false)
  (children [x] '())
  (make-node [x coll] x)

  Track
  (branch? [track]
    (pos? (:favoritings_count track)))
  (children [track]
    (if-let [coll (:favoriters track)]
      coll
      (get-favoriters track)))
  (make-node [track favoriters]
    (assoc track :favoriters favoriters))

  User
  (branch? [user]
    (pos? (:public_favorites_count user)))
  (children [user]
    (if-let [coll (:favorites user)]
      coll
      (get-favorites  user)))
  (make-node [user favorites]
    (assoc user :favorites favorites)))

(defn make-zipper [root]
  (zip/zipper branch? children make-node root))

;; API Endpoints
(defn user
  ([] (map->User (request Endpoints/MY_DETAILS)))
  ([id] (map->User (request Endpoints/USER_DETAILS id))))

(defn get-favorites-with-id [id]
  (map map->Track (request Endpoints/USER_FAVORITES id)))

(defn get-favoriters-with-id [id]
  (map map->User (request Endpoints/TRACK_FAVORITERS id)))

(defprotocol Favorites
  (get-favorites [user])
  (get-favoriters [track]))

(extend-protocol Favorites
  java.lang.Long
  (get-favorites [id] (get-favorites-with-id id))
  (get-favoriters [id] (get-favoriters-with-id id))
  User
  (get-favorites [user] (get-favorites-with-id (:id user)))
  Track
  (get-favoriters [track] (get-favoriters-with-id (:id track))))

;; Similarity metrics
(defn similarity-metric [this that]
  (let [this (set this)
        that (set that)
        intersection (set/intersection this that)]
    (/ (count intersection)
       (Math/max (count this) (count that)))))
