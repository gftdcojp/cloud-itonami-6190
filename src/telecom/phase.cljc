(ns telecom.phase
  "Phase 0->3 staged rollout -- the telecom-operator analog of `cloud-
  itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- line intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds identity verification + billing-
                                 dispute screening writes, still
                                 approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:line/intake` (no capital risk yet)
                                 may auto-commit. `:actuation/
                                 provision-number`/`:actuation/
                                 suppress-billing-record` NEVER auto-
                                 commit, at any phase.

  `:actuation/provision-number`/`:actuation/suppress-billing-record`
  are deliberately ABSENT from every phase's `:auto` set, including
  phase 3 -- a permanent structural fact, not a rollout milestone
  still to come. Provisioning a real E.164 number and suppressing a
  real billing record are the two real-world legal acts this actor
  performs; both are always a human telecom operator's call. `telecom.
  governor`'s `:actuation/provision-number`/`:actuation/suppress-
  billing-record` high-stakes gate enforces the same invariant
  independently -- two layers, not one, agree on this. `:billing/
  screen` is likewise never auto-eligible, at any phase -- the same
  posture every sibling's screening op has. Phase 3's `:auto` set here
  has only ONE member (`:line/intake`) -- this domain has no separate
  no-capital-risk 'file' lifecycle distinct from the line record
  itself.")

(def read-ops  #{})
(def write-ops #{:line/intake :identity/verify :billing/screen
                 :actuation/provision-number :actuation/suppress-billing-record})

;; NOTE the invariant: `:actuation/provision-number`/`:actuation/
;; suppress-billing-record` are members of `write-ops` (governor-gated
;; like any write) but are NEVER members of any phase's `:auto` set
;; below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                          :auto #{}}
   1 {:label "assisted-intake"  :writes #{:line/intake}                                              :auto #{}}
   2 {:label "assisted-verify"  :writes #{:line/intake :identity/verify :billing/screen}              :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:line/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:actuation/provision-number`/`:actuation/suppress-billing-record`
    are never auto-eligible at any phase, so they always escalate once
    the governor clears them (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Telecom Access Governor verdict to a base disposition before
  the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
