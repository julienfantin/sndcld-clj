(ns sndcld-clj.metrics
  (:require [clojure.set :as set]))

(defn- ensure-set [x]
  (if (set? x) x (set x)))

(defn similarity [this that]
  "Return the intersection of two collections, normalized by the size of
the larger one. Higher means more similar, max is 1."
  (let [this (ensure-set this)
        that (ensure-set that)
        intersection (set/intersection this that)]
    (/ (count intersection)
       (Math/max (count this) (count that)))))

