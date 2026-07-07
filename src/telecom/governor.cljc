(ns telecom.governor
  "Telecom Access Governor -- the independent compliance layer that
  earns the Telecom Advisor the right to commit. The LLM has no notion
  of national numbering-plan law, whether a line's own recorded E.164
  number is even syntactically valid, whether a line's own identity
  verification has actually been completed with full evidence, whether
  a billing dispute against the line has actually stayed unresolved,
  or when an act stops being a draft and becomes a real-world number
  provisioning or billing-record suppression, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD --
  the telecom-operator analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated numbering-plan spec-basis, incomplete evidence, a
  malformed E.164 number, an unresolved billing dispute, or a double
  provisioning/suppression). The confidence/actuation gate is SOFT: it
  asks a human to look (low confidence / actuation), and the human may
  approve -- but see `telecom.phase`: for `:stake :actuation/provision-
  number`/`:actuation/suppress-billing-record` (a real-world act) NO
  phase ever allows auto-commit either. Two independent layers agree
  that actuation is always a human call.

    1. Spec-basis                  -- did the identity proposal cite
                                       an OFFICIAL numbering-plan
                                       source (`telecom.facts`), or
                                       invent one?
    2. Evidence incomplete         -- for `:actuation/provision-
                                       number`/`:actuation/suppress-
                                       billing-record`, has the line
                                       actually been identity-verified
                                       with a full evidence checklist
                                       on file?
    3. E.164 format invalid        -- for `:actuation/provision-
                                       number`, INDEPENDENTLY recompute
                                       whether the line's own recorded
                                       E.164 number is syntactically
                                       valid (`telecom.registry/e164-
                                       invalid-format?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. The FIRST
                                       instance of this fleet's format/
                                       syntactic-validity check family
                                       (see `telecom.registry`'s ns
                                       docstring for the grep-verified
                                       precedent check).
    4. Billing dispute unresolved   -- reported by THIS proposal itself
                                       (a `:billing/screen` that just
                                       found an unresolved dispute), or
                                       already on file for the line
                                       (`:billing/screen`/`:actuation/
                                       suppress-billing-record`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       ...(twenty-five prior
                                       siblings)... established -- the
                                       TWENTY-SIXTH distinct
                                       application of this exact
                                       discipline, and the FIRST
                                       specifically for a billing-
                                       dispute concept. Like the
                                       fifteen most recent siblings'
                                       equivalent checks, this is
                                       exercised in tests/demo via
                                       `:billing/screen` DIRECTLY, not
                                       via an actuation op against an
                                       unscreened line -- see this ns's
                                       own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       provision-number`/`:actuation/
                                       suppress-billing-record` (REAL
                                       acts) -> escalate.

  Two more guards, double-provisioning/double-suppression prevention,
  are enforced but NOT listed as numbered HARD checks above because
  they need no upstream comparison at all -- `already-provisioned-
  violations`/`already-suppressed-violations` refuse to provision a
  number/suppress a billing record for the SAME line twice, off
  dedicated `:number-provisioned?`/`:billing-record-suppressed?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean,
  not status' discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320)."
  (:require [telecom.facts :as facts]
            [telecom.registry :as registry]
            [telecom.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Provisioning a real E.164 number and suppressing a real billing
  record are the two real-world actuation events this actor performs
  -- a two-member set, matching every prior dual-actuation sibling's
  shape. Note that `:actuation/suppress-billing-record` is the SECOND
  negative actuation in this fleet (after `water.governor`'s
  `:actuation/suppress-alert`) -- withholding/silencing a record, not
  issuing one -- see this actor's own ADR-0001 Decision 1."
  #{:actuation/provision-number :actuation/suppress-billing-record})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:identity/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  numbering-plan requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:identity/verify :actuation/provision-number :actuation/suppress-billing-record} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は番号計画要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/provision-number`/`:actuation/suppress-billing-
  record`, the jurisdiction's required identity-verification-record/
  e164-number-assignment-record/CDR-integrity-attestation/billing-
  dispute-log evidence must actually be satisfied -- do not trust the
  advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/provision-number :actuation/suppress-billing-record} op)
    (let [ln (store/line st subject)
          verification (store/identity-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction ln) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(本人確認記録/E.164番号割当記録/通話記録完全性証明/請求紛争台帳等)が充足していない状態での提案"}]))))

(defn- e164-format-invalid-violations
  "For `:actuation/provision-number`, INDEPENDENTLY recompute whether
  the line's own recorded E.164 number is syntactically valid via
  `telecom.registry/e164-invalid-format?` -- needs no proposal
  inspection or stored-verdict lookup at all, since its inputs are a
  permanent ground-truth field already on the line."
  [{:keys [op subject]} st]
  (when (= op :actuation/provision-number)
    (let [ln (store/line st subject)]
      (when (registry/e164-invalid-format? ln)
        [{:rule :e164-format-invalid
          :detail (str subject " の記録番号(" (:e164-number ln) ")はE.164形式として不正")}]))))

(defn- billing-dispute-unresolved-violations
  "An unresolved billing dispute -- reported by THIS proposal (e.g. a
  `:billing/screen` that itself just found one), or already on file in
  the store for the line (`:billing/screen`/`:actuation/suppress-
  billing-record`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        line-id (when (contains? #{:billing/screen :actuation/suppress-billing-record} op) subject)
        hit-on-file? (and line-id (= :unresolved (:verdict (store/billing-screen-of st line-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :billing-dispute-unresolved
        :detail "未解決の請求紛争がある状態での請求記録抑制提案は進められない"}])))

(defn- already-provisioned-violations
  "For `:actuation/provision-number`, refuses to provision a number for
  the SAME line twice, off a dedicated `:number-provisioned?` fact
  (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/provision-number)
    (when (store/line-already-provisioned? st subject)
      [{:rule :already-provisioned
        :detail (str subject " は既に番号発行済み")}])))

(defn- already-suppressed-violations
  "For `:actuation/suppress-billing-record`, refuses to suppress a
  billing record for the SAME line twice, off a dedicated `:billing-
  record-suppressed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/suppress-billing-record)
    (when (store/line-already-suppressed? st subject)
      [{:rule :already-suppressed
        :detail (str subject " は既に請求記録抑制済み")}])))

(defn check
  "Censors a Telecom Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (e164-format-invalid-violations request st)
                           (billing-dispute-unresolved-violations request proposal st)
                           (already-provisioned-violations request st)
                           (already-suppressed-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
