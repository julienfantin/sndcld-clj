(ns sndcld-clj.resources
  (:import com.soundcloud.api.Endpoints)
  (:require [sndcld-clj.api :as api]))

;; Model
(defrecord Resource [endpoint id result-mapping-fn])
(defrecord Track [id])
(defrecord User [id])

;; Protocols
(defprotocol Subresources
  "A Protocol for managing resources and their associated subresources."
  (subresource [this]))

(extend-protocol Subresources
  Object
  (subresource [this] nil)
  Track
  (subresource [this] (Resource. Endpoints/TRACK_FAVORITERS (:id this) map->User))
  User
  (subresource [this] (Resource. Endpoints/USER_FAVORITES (:id this) map->Track)))

;; Convenience
(defn me []
  "Request and return the user currently logged in."
  (first (api/request (Resource. Endpoints/MY_DETAILS nil map->User))))

(defn track []
  (first (api/request (subresource (me)))))
