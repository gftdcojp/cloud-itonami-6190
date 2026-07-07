(ns telecom.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/provision-number`/`:actuation/suppress-
  billing-record` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [telecom.phase :as phase]))

(deftest provision-number-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real number provisioning"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/provision-number))
          (str "phase " n " must not auto-commit :actuation/provision-number")))))

(deftest suppress-billing-record-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real billing-record suppression"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/suppress-billing-record))
          (str "phase " n " must not auto-commit :actuation/suppress-billing-record")))))

(deftest billing-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :billing/screen))
          (str "phase " n " must not auto-commit :billing/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":line/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:line/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :line/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/provision-number} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/suppress-billing-record} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :line/intake} :commit)))))
