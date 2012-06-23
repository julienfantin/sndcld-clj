(ns sndcld-clj.core-test
  (:use clojure.test
        sndcld-clj.core))

(defonce dsturb (-> "/Users/julienfantin/Projects/sndcld-clj/test/fixtures/me.clj"
                slurp
                read-string
                map->User))

(deftest user
  (testing "Should have an id."
    (is (not (nil? (:id dsturb)))))
  (testing "Should not have any favorites yet."
    (is (empty? (:favorite dsturb))))
  (testing "Should have no tracks."
    (is (empty? (:tracks dsturb)))))

(deftest user->favorites
  (testing ""
    (let [z (make-zipper dsturb)]
      (-> z zip/down))))

(deftest similarity-metric
  (testing "Two identical sets should get the maximum possible value of 1.0."
    (is (== 1.0 (similarity-metric (range 1 10) (range 1 10)))))
  (testing "When comparing two sets, a same number of common favorites should yield a higher metric if the set is small"
    (is (< (similarity-metric (set (range 0 5)) (set (range -10 1)))
           (similarity-metric (set (range 0 5)) #{0})))))
