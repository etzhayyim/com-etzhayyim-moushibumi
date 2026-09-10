(ns moushibumi.social-test
  (:require [clojure.test :refer [deftest is]]
            [moushibumi.methods.social :as social]
            [moushibumi.cells.social-post.state-machine :as machine]))

(deftest publication-is-dry-run-only
  (let [post (social/draft-observation-post "public comment" "observed" ["cid:a" "cid:b"])]
    (is (= ":dry-run" (get post ":post/status")))
    (is (false? (get post ":post/server-held-key")))
    (is (thrown? Exception (social/build-live post)))))

(deftest publication-state-machine-enforces-source-gate
  (is (= machine/phase-drafted
         (get-in (machine/transition-to-drafted
                  {"subject" "public comment" "sources" ["cid:a" "cid:b"]})
                 ["cell_state" "phase"])))
  (is (= machine/phase-refused
         (get-in (machine/transition-to-drafted
                  {"subject" "public comment" "sources" ["cid:a"]})
                 ["cell_state" "phase"]))))
