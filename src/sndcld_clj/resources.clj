(ns sndcld-clj.resources
  (:import com.soundcloud.api.Endpoints)
  (:require [sndcld-clj.api :as api]))

;; Model
(defrecord Resource [endpoint id result-mapping-fn])
(defrecord Track [id])
(defrecord User [id])

;; Requests
(defn- ensure-vector [vec-or-x]
  (if (vector? vec-or-x) vec-or-x (vector vec-or-x)))

(defn- convert-api-response [resource response]
  (let [f (:result-mapping-fn resource)]
    (reduce (fn [coll res]
              (conj coll (f res)))
            []
            (ensure-vector response))))

(defn request [resource]
  (let [response (api/request (:endpoint resource) (:id resource))]
    (convert-api-response resource response)))

;; Protocols
(defprotocol Subresources
  "A Protocol for managing resources and their associated subresources."
  (subresource [this]))

(extend-protocol Subresources
  Track
  (subresource [this] (Resource. Endpoints/TRACK_FAVORITERS (:id this) map->User))
  User
  (subresource [this] (Resource. Endpoints/USER_FAVORITES (:id this) map->Track)))

;; Convenience
(defn me []
  "Request and return the user currently logged in."
  (first (request (Resource. Endpoints/MY_DETAILS nil map->User))))
