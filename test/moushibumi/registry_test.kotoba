(ns moushibumi.registry-test
  (:require [clojure.edn :as edn]
            [kotoba.lang.text :as str]
            [clojure.test :refer [deftest is]]))

(def registry (edn/read-string (slurp "registry/targets.seed.edn")))
(def targets (get registry "targets"))

(deftest registry-has-targets
  (is (map? registry))
  (is (seq targets)))

(deftest target-identities-are-unique
  (let [ids (mapv #(get % "targetId") targets)]
    (is (every? seq ids))
    (is (= (count ids) (count (set ids))))))

(deftest seed-is-fail-closed
  (doseq [target targets]
    (is (= "unverified-seed" (get target "verificationStatus"))
        (get target "targetId"))))

(deftest provenance-and-freshness-are-present
  (doseq [target targets]
    (is (str/starts-with? (get target "provenance" "") "https://")
        (get target "targetId"))
    (is (seq (get target "lastVerified")) (get target "targetId"))))

(deftest worldwide-jurisdiction-coverage
  (is (every? #(seq (get % "jurisdiction")) targets))
  (is (<= 12 (count (set (map #(get % "jurisdiction") targets))))))

(deftest political-neutrality-boundary-is-explicit
  (is (every? #(not (str/blank? (get % "notes" ""))) targets))
  (let [notes (map #(get % "notes" "") targets)]
    (is (some #(str/includes? % "公選法") notes))
    (is (<= 5 (count (filter #(or (str/includes? % "公選法")
                                  (str/includes? (str/lower %) "political-neutrality"))
                            notes))))))

(deftest freshness-window-is-positive-integer
  (let [days (get registry "freshnessWindowDays")]
    (is (integer? days))
    (is (pos? days))))
