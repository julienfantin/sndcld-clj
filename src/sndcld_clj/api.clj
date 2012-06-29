(ns sndcld-clj.api
  (:import com.soundcloud.api.ApiWrapper)
  (:import com.soundcloud.api.Env)
  (:import com.soundcloud.api.Token)
  (:import com.soundcloud.api.Request)
  (:import com.soundcloud.api.Http)
  (:import com.soundcloud.api.Endpoints)
  (:require [clojure.data.json :as json]))

(def ^:const client-id "cab243f44bc34d57e99fb4e254d7ca3b")
(def ^:const client-secret "d1c880b44394cc3431ce4fbd0364aa26")

(defonce wrapper
  (ApiWrapper. client-id client-secret nil nil Env/LIVE))

(defn login [user password]
  (.login wrapper user password (into-array String [Token/SCOPE_NON_EXPIRING])))

(defn request [endpoint & args]
  "Issue a request for the given endpoint and optional args. Return a
native clj map of the response body on success."
  (let [request (Request/to endpoint (to-array args))
        response (.get wrapper request)
        status (.getStatusCode (.getStatusLine response))
        body-string (Http/getString response)]
    (if (= 200 status)
      (json/read-json body-string)
      (println (str "Error " status " :\n" body-string)))))
