(ns sndcld-clj.api
  (:import com.soundcloud.api.ApiWrapper)
  (:import com.soundcloud.api.Env)
  (:import com.soundcloud.api.Token)
  (:import com.soundcloud.api.Request)
  (:import com.soundcloud.api.Http)
  (:import com.soundcloud.api.Endpoints)
  (:import java.util.concurrent.LinkedBlockingQueue)
  (:require [sndcld-clj.persistence :as persist]
            [sndcld-clj.resources :as resources]
            [clojure.data.json :as json]))

(def ^:const client-id "cab243f44bc34d57e99fb4e254d7ca3b")
(def ^:const client-secret "d1c880b44394cc3431ce4fbd0364aa26")

(def wrapper
  "Java API wrapper initialized with app-wide settings."
  (ApiWrapper. client-id client-secret nil nil Env/LIVE))

(defn login [user password]
  "Simple auth method, i.e. NOT OAuth."
  (.login wrapper user password (into-array String [Token/SCOPE_NON_EXPIRING])))

(defn- api-request [endpoint & args]
  "Issue a request for the given endpoint and optional args. Return a
map of the response on success, throw otherwise."
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
  "Request a Resource, and return a converted response."
  (let [parent-resource (:parent-resource resource)
        id (if parent-resource (:id parent-resource))
        response (api-request (:endpoint resource) id)]
    (convert-api-response resource response)))

;; Asynchronous interface
(declare run)

(def ^:const agents-count 25)

(def workers-count (atom 0))

(def queue
  "A queue used to line-up Resource requests."
  (LinkedBlockingQueue.))

(defn async-request [resource continuation]
  "Queue a resource request and a continuation that will be called with
  the result on success."
  (.put queue {:resource resource
               :continuation continuation})
  (println (str "Queue size : ") (.size queue)))

(defn- process-async-request [queue]
  (try
    (let [element (.take queue)
          _ (println (str (swap! workers-count inc) " workers"))
          resource (:resource element)
          continuation (:continuation element)
          result (request resource)]
      (map persist/cache-resource result)
      (if-let [parent-resource (:parent-resource resource)]
        (persist/cache-subresources parent-resource result))
      (continuation result)
      queue) ;make sure we preserve state between runs
    (catch Exception e
      (println e)
      queue)
    (finally
      (swap! workers-count dec)
      (run *agent*))))

(defn create-agents []
  "Create a pool of agents that will process resource requests put on
`queue`."
  (set (repeatedly agents-count #(agent queue))))

(def agents (create-agents))

(defn- paused? [agent]
  (::paused (meta agent)))

(defn pause
  "Mark agents as paused."
  ([] (doseq [agent agents] (pause agent)))
  ([agent] (alter-meta! agent assoc ::paused true)))

(defn resume
  "Unmark agents as paused."
  ([] (doseq [agent agents] (resume agent)))
  ([agent]
     (when (paused? agent)
       (alter-meta! agent dissoc ::paused)
       (run agent))))

(defn run
  "Run agents in an unbounded thread pool."
  ([]
     (resume)
     (doseq [agent agents]
       (run agent)))
  ([agent]
     (when (agents agent)
       (when-not (paused? agent)
         (send-off agent process-async-request)))))

(defn reset []
  "Clear the queue, recreate agents, resume and run."
  (pause)
  (.clear queue)
  (def agents (create-agents)))

;; Convenience
(defn me []
  "Request and return the user currently logged in."
  (first (request (resources/->Resource Endpoints/MY_DETAILS nil resources/map->User))))

(defn track []
  (first (request (resources/subresource (me)))))
