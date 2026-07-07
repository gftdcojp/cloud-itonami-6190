(ns telecom.registry-test
  (:require [clojure.test :refer [deftest is]]
            [telecom.registry :as r]))

;; ----------------------------- e164-invalid-format? -----------------------------

(deftest valid-when-well-formed
  (is (not (r/e164-invalid-format? {:e164-number "+819012345678"})))
  (is (not (r/e164-invalid-format? {:e164-number "+12025550123"}))))

(deftest invalid-when-missing-leading-plus-or-leading-zero-or-wrong-length
  (is (r/e164-invalid-format? {:e164-number "0312345678"}) "no leading +")
  (is (r/e164-invalid-format? {:e164-number "+0312345678"}) "leading zero after +")
  (is (r/e164-invalid-format? {:e164-number "+1234567"}) "too short (7 digits)")
  (is (r/e164-invalid-format? {:e164-number "+1234567890123456"}) "too long (16 digits)"))

(deftest invalid-is-true-on-missing-field
  (is (r/e164-invalid-format? {}))
  (is (r/e164-invalid-format? {:e164-number nil})))

;; ----------------------------- register-number-provisioning -----------------------------

(deftest provisioning-is-a-draft-not-a-real-provisioning
  (let [result (r/register-number-provisioning "line-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest provisioning-assigns-provisioning-number
  (let [result (r/register-number-provisioning "line-1" "JPN" 7)]
    (is (= (get result "provisioning_number") "JPN-PRV-000007"))
    (is (= (get-in result ["record" "line_id"]) "line-1"))
    (is (= (get-in result ["record" "kind"]) "number-provisioning-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest provisioning-validation-rules
  (is (thrown? Exception (r/register-number-provisioning "" "JPN" 0)))
  (is (thrown? Exception (r/register-number-provisioning "line-1" "" 0)))
  (is (thrown? Exception (r/register-number-provisioning "line-1" "JPN" -1))))

;; ----------------------------- register-billing-suppression -----------------------------

(deftest suppression-is-a-draft-not-a-real-suppression
  (let [result (r/register-billing-suppression "line-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest suppression-assigns-suppression-number
  (let [result (r/register-billing-suppression "line-1" "JPN" 3)]
    (is (= (get result "suppression_number") "JPN-SUP-000003"))
    (is (= (get-in result ["record" "line_id"]) "line-1"))
    (is (= (get-in result ["record" "kind"]) "billing-suppression-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest suppression-validation-rules
  (is (thrown? Exception (r/register-billing-suppression "" "JPN" 0)))
  (is (thrown? Exception (r/register-billing-suppression "line-1" "" 0)))
  (is (thrown? Exception (r/register-billing-suppression "line-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-number-provisioning "line-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-number-provisioning "line-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-PRV-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-PRV-000001" (get-in hist2 [1 "record_id"])))))
