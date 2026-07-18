(ns moushibumi.murakumo-test
  (:require [clojure.test :refer [deftest is testing]]
            [moushibumi.murakumo :as moushibumi]))

(def full-attestations
  (into {}
        (map (fn [gate] [gate (str "attested-" (name gate))]))
        (distinct
         (mapcat (fn [spec]
                   (concat (:required-gates spec)
                           (:agent-on-behalf-gates spec)
                           [:election-info-read-side-only-baseline]))
                 (vals moushibumi/cell-specs)))))

(deftest maps-all-legacy-moushibumi-cells
  (is (= #{"moushibumi_compose"
           "moushibumi_intake"
           "moushibumi_opportunity_match"
           "moushibumi_status_track"
           "moushibumi_submit"
           "moushibumi_target_registry"
           "moushibumi_voter_info"}
         (set (map :legacy-cell (vals moushibumi/cell-specs))))))

(deftest r0-gates-block-effects
  (let [plan (moushibumi/cell-plan :opportunity-match
                                   {:member-did "did:example:member"
                                    :target-id "jp-egov-public-comment"
                                    :computed-at "2026-06-29T00:00:00Z"})]
    (is (= :blocked (:status plan)))
    (is (= [:council-charter-attestation
            :silen-moushibumi-baseline-review
            :political-neutrality-info-procedure-only-baseline
            :member-consent-baseline
            :own-voice-only-baseline
            :upl-boundary-no-advice-no-representation-baseline
            :encrypted-pii-and-opinion-envelope-baseline
            :murakumo-only-inference-baseline
            :state-aligned-flag-passthrough-baseline
            :no-political-opinion-profiling-baseline
            :neutral-match-framing-baseline
            :data-minimization-baseline]
           (:missing-gates plan)))
    (is (empty? (:effects plan)))))

(deftest attested-self-submit-emits-submission-record
  (let [plan (moushibumi/cell-plan :submit
                                   {:attestations full-attestations
                                    :member-did "did:example:member"
                                    :session-id "session-001"
                                    :target-id "jp-egov-public-comment"
                                    :draft-id "draft-001"
                                    :submission-id "submission-001"
                                    :channel-kind "public-comment"
                                    :computed-at "2026-06-29T00:00:00Z"
                                    :record {:tid "submission-001"
                                             :portalUrl "https://public-comment.e-gov.go.jp/"}})
        effect (first (:effects plan))]
    (is (= :ready (:status plan)))
    (is (= :mst/put-record (:op effect)))
    (is (= moushibumi/actor-did (:actor effect)))
    (is (= "com.etzhayyim.moushibumi.submissionRecord" (:collection effect)))
    (is (= "submission-001" (:rkey effect)))
    (is (= "member-self-submit" (get-in effect [:record :mode])))
    (is (= true (get-in effect [:record :memberSelfSubmissionDefault])))))

(deftest voter-info-is-read-side-only
  (let [plan (moushibumi/cell-plan :voter-info
                                   {:attestations full-attestations
                                    :target-id "jp-election-info"
                                    :computed-at "2026-06-29T00:00:00Z"})]
    (is (= :ready (:status plan)))
    (is (empty? (:records plan)))
    (is (empty? (:effects plan)))))

(deftest daikou-submit-keeps-r3-gates
  (testing "agent-on-behalf requires R3 and administrative-scrivener clearance"
    (let [attestations (apply dissoc full-attestations
                              [:r3-daikou-activation-adr
                               :council-lv7-unanimity-attestation
                               :administrative-scrivener-clearance-attestation
                               :per-submission-agent-consent-baseline])
          plan (moushibumi/cell-plan :submit
                                     {:attestations attestations
                                      :submit-mode "agent-on-behalf"
                                      :channel-kind "petition"
                                      :submission-id "submission-002"})]
      (is (= :blocked (:status plan)))
      (is (= [:r3-daikou-activation-adr
              :council-lv7-unanimity-attestation
              :administrative-scrivener-clearance-attestation
              :per-submission-agent-consent-baseline]
             (:missing-gates plan)))
      (is (empty? (:effects plan)))))
  (testing "self-submit does not require daikou gates"
    (let [attestations (apply dissoc full-attestations
                              [:r3-daikou-activation-adr
                               :council-lv7-unanimity-attestation
                               :administrative-scrivener-clearance-attestation
                               :per-submission-agent-consent-baseline])
          plan (moushibumi/cell-plan :submit
                                     {:attestations attestations
                                      :submit-mode "member-self-submit"
                                      :channel-kind "petition"
                                      :submission-id "submission-003"})]
      (is (= :ready (:status plan)))
      (is (= ["com.etzhayyim.moushibumi.submissionRecord"]
             (map :collection (:effects plan)))))))

(deftest election-info-submit-remains-extra-gated
  (let [attestations (dissoc full-attestations :election-info-read-side-only-baseline)
        plan (moushibumi/cell-plan :submit
                                   {:attestations attestations
                                    :channel-kind "election-info"
                                    :submission-id "submission-election"})]
    (is (= :blocked (:status plan)))
    (is (= [:election-info-read-side-only-baseline] (:missing-gates plan)))
    (is (empty? (:effects plan)))))

(deftest cell-specific-gates-remain-specific
  (testing "target registry keeps official-source provenance"
    (let [attestations (dissoc full-attestations :official-source-provenance-baseline)
          plan (moushibumi/cell-plan :target-registry {:attestations attestations})]
      (is (= [:official-source-provenance-baseline] (:missing-gates plan)))))
  (testing "compose keeps drafting-assist only"
    (let [attestations (dissoc full-attestations :drafting-assist-only-baseline)
          plan (moushibumi/cell-plan :compose {:attestations attestations})]
      (is (= [:drafting-assist-only-baseline] (:missing-gates plan)))))
  (testing "status track keeps aggregate-first publication"
    (let [attestations (dissoc full-attestations :aggregate-first-publication-baseline)
          plan (moushibumi/cell-plan :status-track {:attestations attestations})]
      (is (= [:aggregate-first-publication-baseline] (:missing-gates plan))))))

(deftest all-cell-plans-ready-when-attested
  (let [plans (moushibumi/all-cell-plans {:attestations full-attestations
                                          :member-did "did:example:member"
                                          :session-id "session-001"
                                          :target-id "jp-egov-public-comment"
                                          :draft-id "draft-001"
                                          :submission-id "submission-001"
                                          :channel-kind "public-comment"
                                          :jurisdiction "jpn"
                                          :computed-at "2026-06-29T00:00:00Z"})]
    (is (= (set (keys moushibumi/cell-specs)) (set (keys plans))))
    (is (every? #(= :ready (:status %)) (vals plans)))
    (is (= 6 (count (mapcat :effects (vals plans)))))))
