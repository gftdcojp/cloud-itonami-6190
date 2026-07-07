(ns telecom.governor-contract-test
  "The governor contract as executable tests -- the telecom-operator
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    Telecom Advisor never provisions a number or suppresses a billing
    record the Telecom Access Governor would reject, `:actuation/
    provision-number`/`:actuation/suppress-billing-record` NEVER
    auto-commit at any phase, `:line/intake` (no direct capital risk)
    MAY auto-commit when clean, and every decision (commit OR hold)
    leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [telecom.store :as store]
            [telecom.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :telecom-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving an identity
  verification on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :identity/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through billing-dispute screening -> approve,
  leaving a screening on file. Only safe to call for a line whose
  dispute status has already resolved -- an unresolved dispute HARD-
  holds the screen itself (see
  `billing-dispute-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :billing/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :line/intake :subject "line-1"
                   :patch {:id "line-1" :holder-name "Sakura Community Network"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Community Network" (:holder-name (store/line db "line-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest identity-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :identity/verify :subject "line-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/identity-verification-of db "line-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "an identity/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :identity/verify :subject "line-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/identity-verification-of db "line-1")) "no verification written"))))

(deftest provision-number-without-verification-is-held
  (testing "actuation/provision-number before any identity verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/provision-number :subject "line-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest e164-format-invalid-is-held
  (testing "a line whose own recorded E.164 number is malformed -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "line-3")
          res (exec-op actor "t5" {:op :actuation/provision-number :subject "line-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:e164-format-invalid} (-> (store/ledger db) last :basis)))
      (is (empty? (store/provisioning-history db))))))

(deftest billing-dispute-is-held-and-unoverridable
  (testing "an unresolved billing dispute on a line -> HOLD, and never reaches request-approval -- exercised via :billing/screen DIRECTLY, not via the actuation op against an unscreened line (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's and water's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :billing/screen :subject "line-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:billing-dispute-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/billing-screen-of db "line-4")) "no clearance written"))))

(deftest provision-number-always-escalates-then-human-decides
  (testing "a clean, fully-verified, well-formed line still ALWAYS interrupts for human approval -- actuation/provision-number is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "line-1")
          r1 (exec-op actor "t7" {:op :actuation/provision-number :subject "line-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, provisioning record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:number-provisioned? (store/line db "line-1"))))
          (is (= 1 (count (store/provisioning-history db))) "one draft provisioning record"))))))

(deftest suppress-billing-record-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-dispute line still ALWAYS interrupts for human approval -- actuation/suppress-billing-record is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "line-1")
          _ (screen! actor "t8pre2" "line-1")
          r1 (exec-op actor "t8" {:op :actuation/suppress-billing-record :subject "line-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, suppression record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:billing-record-suppressed? (store/line db "line-1"))))
          (is (= 1 (count (store/suppression-history db))) "one draft suppression record"))))))

(deftest provision-number-double-provisioning-is-held
  (testing "provisioning the same line's number twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "line-1")
          _ (exec-op actor "t9a" {:op :actuation/provision-number :subject "line-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/provision-number :subject "line-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-provisioned} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/provisioning-history db))) "still only the one earlier provisioning"))))

(deftest suppress-billing-record-double-suppression-is-held
  (testing "suppressing the same line's billing record twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "line-1")
          _ (screen! actor "t10pre2" "line-1")
          _ (exec-op actor "t10a" {:op :actuation/suppress-billing-record :subject "line-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/suppress-billing-record :subject "line-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-suppressed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/suppression-history db))) "still only the one earlier suppression"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :line/intake :subject "line-1"
                          :patch {:id "line-1" :holder-name "Sakura Community Network"}} operator)
      (exec-op actor "b" {:op :identity/verify :subject "line-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
