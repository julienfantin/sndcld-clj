(ns sndcld-clj.resources
  (:import com.soundcloud.api.Endpoints))

;; Model
(defrecord Resource [endpoint parent-resource result-mapping-fn])
(defrecord Track [id])
(defrecord User [id])

;; Protocols
(defprotocol Subresources
  "A Protocol for managing resources and their associated subresources."
  (subresource [this]))

(extend-protocol Subresources
  Track
  (subresource [this] (->Resource Endpoints/TRACK_FAVORITERS this map->User))
  User
  (subresource [this] (->Resource Endpoints/USER_FAVORITES this map->Track))
  Object
  (subresource [this] nil))

