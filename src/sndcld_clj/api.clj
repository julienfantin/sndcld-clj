(ns sndcld-clj.api
  (:import com.soundcloud.api.ApiWrapper)
  (:import com.soundcloud.api.Env)
  (:import com.soundcloud.api.Token)
  (:import com.soundcloud.api.Request)
  (:import com.soundcloud.api.Http)
  (:import com.soundcloud.api.Endpoints)
  (:import java.util.concurrent.LinkedBlockingQueue)
  (:require [clojure.data.json :as json]))

(def ^:const client-id "cab243f44bc34d57e99fb4e254d7ca3b")
(def ^:const client-secret "d1c880b44394cc3431ce4fbd0364aa26")

(defonce wrapper
  (ApiWrapper. client-id client-secret nil nil Env/LIVE))

(defn login [user password]
  (.login wrapper user password (into-array String [Token/SCOPE_NON_EXPIRING])))

(defn- api-request [endpoint & args]
  "Issue a request for the given endpoint and optional args. Return a
native clj map of the response body on success."
  (let [request (Request/to endpoint (to-array args))
        response (.get wrapper request)
        status (.getStatusCode (.getStatusLine response))
        body-string (Http/getString response)]
    (if (= 200 status)
      (json/read-json body-string)
      (throw
       (new Exception
            (str "Request failed: error " status " :\n" body-string))))))

;; Resources wrapper
(defn- ensure-vector [vec-or-x]
  (if (vector? vec-or-x) vec-or-x (vector vec-or-x)))

(defn- convert-api-response [resource response]
  (let [f (:result-mapping-fn resource)]
    (reduce (fn [coll res]
              (conj coll (f res)))
            []
            (ensure-vector response))))

(defn request [resource]
  (let [response (api-request (:endpoint resource) (:id resource))]
    (convert-api-response resource response)))

;; Asynchronous interface
(declare run)

(def ^:const agents-count 25)

(def queue (LinkedBlockingQueue.))

(defn async-request [resource continuation]
  (.put queue {:resource resource
               :continuation continuation}))

(defn- process-async-request [queue]
  (try
    (let [element (.take queue)
          resource (:resource element)
          continuation (:continuation element)
          response (request resource)]
      (continuation response)
      queue) ;make sure we preserve state between runs
    (catch Exception e
      (println e)
      queue)
    (finally (run *agent*))))

(def agents (set (repeatedly agents-count #(agent queue))))

(defn- paused? [agent]
  (::paused (meta agent)))

(defn pause
  ([] (doseq [agent agents] (pause agent)))
  ([agent] (alter-meta! agent assoc ::paused true)))

(defn resume
  ([] (doseq [agent agents] (resume agent)))
  ([agent]
     (alter-meta! agent dissoc ::paused)
     (run agent)))

(defn run
  ([] (doseq [agent agents]
        (run agent)))
  ([agent]
     (when (agents agent)
       (when-not (paused? agent)
         (send-off agent process-async-request)))))
