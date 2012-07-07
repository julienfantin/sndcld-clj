(ns sndcld-clj.cps
  (:use [clojure.algo.monads :only [domonad cont-m run-cont call-cc
                                    m-when with-monad maybe-t sequence-t m-chain]]))

;; API not in our control that we want to use in CPS
(defn query-async [query callback]
  (future (Thread/sleep 1000)
          (callback (str "resp: " query))))

;; CPS implementation details - provided by a CPS library of some sort
(with-monad cont-m
  (def mk-cont m-result))

(def cps-monad (maybe-t cont-m))

(defn mk-cps
  "wrap a function `f` taking only a callback, suitable for use inside cont-m"
  [f]
  (call-cc
   (fn [c] ;`c` is a "callback" to the business logic
     (let [callback (fn [result]
                                        ;build an internal callback for the query function
                                        ;that will invoke `c` to return to biz logic
                      (run-cont (c result)))]
                                        ;when we have a result
                                        ;invoke `c` with the result
       (f callback)
                                        ;execute the query, it will resume the business logic via above
                                        ;internal callback which invokes `c` with the response.
       (mk-cont nil)
                                        ;signal to bail out of business logic, we don't have a result yet.
                                        ;we will resume the business logic when we have a result via `c`
       ))))

;; business logic!
(defn query-something [n]
  (let [doquery (partial query-async (str "query:" n))]
    (mk-cps doquery)))

;; once you invoke this, you're done, you gave up your call stack and
;; can't return anything meaningful, just "result pending". the final operation
;; in the CPS monad must be a side effect.
;; can't skip the remaining biz logic while waiting on a value - have to return
;; something so we don't block - use maybe-m to bail out. each subsequent time
;; the async callback is called, we will get a step further.
(defn cps-bizlogic-6 [x finished-callback]
  (with-monad (maybe-t cont-m)
    (domonad [a (query-something x)
              b (query-something a)
              c (query-something b)]
             (finished-callback c))))

;; (run-cont (cps-bizlogic-6 "query1" prn))

