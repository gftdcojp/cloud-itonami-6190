(ns telecom.registry
  "Pure-function number-provisioning + billing-suppression record
  construction -- an append-only telecom-operator book-of-record
  draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a number-provisioning or
  billing-suppression reference number -- every operator/jurisdiction
  assigns its own reference format. This namespace does NOT invent
  one; it builds a jurisdiction-scoped sequence number and validates
  the record's required fields, the same honest, non-fabricating
  discipline `telecom.facts` uses.

  `e164-invalid-format?` is the FIRST instance of a structural/
  syntactic FORMAT-validity check in this fleet's governor-layer
  history (confirmed absent from every prior sibling's `governor.cljc`
  by grep before writing this docstring, avoiding the false-precedent-
  claim risk `leasing`'s ADR-0001 documents) -- every prior HARD check
  in this fleet's check-family taxonomy compares a MEASURED/RECORDED
  value against a range, threshold, set or unresolved-flag, never
  validates the SYNTACTIC shape of an identifier itself. It is a
  simplified structural E.164 check (leading `+`, no leading zero,
  8-15 total digits) -- not a full ITU-T E.164 numbering-plan
  validator (see `telecom.facts`'s docstring for the same honest-
  scope discipline) -- but a genuine ground-truth recompute on the
  line's own recorded number, independent of any advisor self-report.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real telecom switch/HLR/number-portability database. It
  builds the RECORD an operator would keep, not the act of
  provisioning the number or suppressing the billing record itself
  (that is `telecom.operation`'s `:actuation/provision-number`/
  `:actuation/suppress-billing-record`, always human-gated -- see
  README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  operator's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn e164-invalid-format?
  "Is `line`'s own recorded `:e164-number` NOT a syntactically valid
  E.164 number -- a leading `+`, no leading zero after it, and 8-15
  total digits? A pure ground-truth check against the line's own
  permanent field -- no upstream comparison needed. The FIRST instance
  of this fleet's format/syntactic-validity check family (see ns
  docstring)."
  [{:keys [e164-number]}]
  (or (nil? e164-number)
      (not (re-matches #"\+[1-9]\d{7,14}" e164-number))))

(defn register-number-provisioning
  "Validate + construct the NUMBER-PROVISIONING registration DRAFT --
  the operator's own act of activating a real E.164 number for a
  line. Pure function -- does not touch any real telecom switch/HLR;
  it builds the RECORD an operator would keep. `telecom.governor`
  independently re-verifies the line's own E.164 format validity and
  identity-verification sufficiency, and blocks a double-provisioning
  for the same line, before this is ever allowed to commit."
  [line-id jurisdiction sequence]
  (when-not (and line-id (not= line-id ""))
    (throw (ex-info "number-provisioning: line_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "number-provisioning: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "number-provisioning: sequence must be >= 0" {})))
  (let [provisioning-number (str (str/upper-case jurisdiction) "-PRV-" (zero-pad sequence 6))
        record {"record_id" provisioning-number
                "kind" "number-provisioning-draft"
                "line_id" line-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "provisioning_number" provisioning-number
     "certificate" (unsigned-certificate "NumberProvisioning" provisioning-number provisioning-number)}))

(defn register-billing-suppression
  "Validate + construct the BILLING-SUPPRESSION registration DRAFT --
  the operator's own act of suppressing a real billing record for a
  line. Pure function -- does not touch any real telecom billing
  system; it builds the RECORD an operator would keep. `telecom.
  governor` independently re-verifies the line's own billing-dispute
  resolution status, and blocks a double-suppression for the same
  line, before this is ever allowed to commit. Like `water.registry/
  register-alert-suppression`, this actuation is a NEGATIVE act
  (withholding/silencing a billing record), not a positive one
  (issuing a record) -- the SECOND negative actuation this fleet has
  modeled -- see README `Actuation` and this actor's own ADR-0001
  Decision 1 for the honest framing this makes."
  [line-id jurisdiction sequence]
  (when-not (and line-id (not= line-id ""))
    (throw (ex-info "billing-suppression: line_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "billing-suppression: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "billing-suppression: sequence must be >= 0" {})))
  (let [suppression-number (str (str/upper-case jurisdiction) "-SUP-" (zero-pad sequence 6))
        record {"record_id" suppression-number
                "kind" "billing-suppression-draft"
                "line_id" line-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "suppression_number" suppression-number
     "certificate" (unsigned-certificate "BillingSuppression" suppression-number suppression-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
