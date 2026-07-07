(ns telecom.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [telecom.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Community Network" (:holder-name (store/line s "line-1"))))
      (is (= "JPN" (:jurisdiction (store/line s "line-1"))))
      (is (= "+819012345678" (:e164-number (store/line s "line-1"))))
      (is (false? (:billing-dispute-unresolved? (store/line s "line-1"))))
      (is (= "0312345678" (:e164-number (store/line s "line-3"))))
      (is (true? (:billing-dispute-unresolved? (store/line s "line-4"))))
      (is (false? (:number-provisioned? (store/line s "line-1"))))
      (is (false? (:billing-record-suppressed? (store/line s "line-1"))))
      (is (= ["line-1" "line-2" "line-3" "line-4"]
             (mapv :id (store/all-lines s))))
      (is (nil? (store/billing-screen-of s "line-1")))
      (is (nil? (store/identity-verification-of s "line-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/provisioning-history s)))
      (is (= [] (store/suppression-history s)))
      (is (zero? (store/next-provisioning-sequence s "JPN")))
      (is (zero? (store/next-suppression-sequence s "JPN")))
      (is (false? (store/line-already-provisioned? s "line-1")))
      (is (false? (store/line-already-suppressed? s "line-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :line/upsert
                                 :value {:id "line-1" :holder-name "Sakura Community Network"}})
        (is (= "Sakura Community Network" (:holder-name (store/line s "line-1"))))
        (is (= "+819012345678" (:e164-number (store/line s "line-1"))) "unrelated field preserved"))
      (testing "verification / billing-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["line-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/identity-verification-of s "line-1")))
        (store/commit-record! s {:effect :billing-screen/set :path ["line-1"]
                                 :payload {:line-id "line-1" :verdict :resolved}})
        (is (= {:line-id "line-1" :verdict :resolved} (store/billing-screen-of s "line-1"))))
      (testing "number provisioning drafts a record and advances the sequence"
        (store/commit-record! s {:effect :line/mark-provisioned :path ["line-1"]})
        (is (= "JPN-PRV-000000" (get (first (store/provisioning-history s)) "record_id")))
        (is (= "number-provisioning-draft" (get (first (store/provisioning-history s)) "kind")))
        (is (true? (:number-provisioned? (store/line s "line-1"))))
        (is (= 1 (count (store/provisioning-history s))))
        (is (= 1 (store/next-provisioning-sequence s "JPN")))
        (is (true? (store/line-already-provisioned? s "line-1")))
        (is (false? (store/line-already-provisioned? s "line-2"))))
      (testing "billing suppression drafts a record and advances the sequence"
        (store/commit-record! s {:effect :line/mark-suppressed :path ["line-1"]})
        (is (= "JPN-SUP-000000" (get (first (store/suppression-history s)) "record_id")))
        (is (= "billing-suppression-draft" (get (first (store/suppression-history s)) "kind")))
        (is (true? (:billing-record-suppressed? (store/line s "line-1"))))
        (is (= 1 (count (store/suppression-history s))))
        (is (= 1 (store/next-suppression-sequence s "JPN")))
        (is (true? (store/line-already-suppressed? s "line-1")))
        (is (false? (store/line-already-suppressed? s "line-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/line s "nope")))
    (is (= [] (store/all-lines s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/provisioning-history s)))
    (is (= [] (store/suppression-history s)))
    (is (zero? (store/next-provisioning-sequence s "JPN")))
    (is (zero? (store/next-suppression-sequence s "JPN")))
    (store/with-lines s {"x" {:id "x" :holder-name "n" :e164-number "+819012345678"
                             :billing-dispute-unresolved? false
                             :number-provisioned? false :billing-record-suppressed? false
                             :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:holder-name (store/line s "x"))))))
