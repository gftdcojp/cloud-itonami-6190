# ADR-0001: Telecom Advisor ⊣ Telecom Access Governor architecture

## Status

Accepted. `cloud-itonami-isic-6190` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-6190` publishes an OSS business blueprint for
community telecommunications access: number provisioning, call
routing, CDR/SMS record management, and usage-based billing, run by a
qualified operator so a community keeps its own call records and
numbering data. Like every prior actor in this fleet, the blueprint
alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the
same langgraph-clj StateGraph + independent Governor + Phase 0→3
rollout pattern established by `cloud-itonami-isic-6511` (life
insurance) and applied across forty-one prior siblings, most recently
`cloud-itonami-isic-3600` (water-safety operations, this fleet's FIRST
infrastructure/utility vertical).

## Decision

### Decision 1: `:actuation/suppress-billing-record` is this fleet's SECOND negative actuation

Every actuation in this fleet prior to `cloud-itonami-isic-3600` was a
POSITIVE act: issuing or finalizing a real-world record (a policy, a
certificate, a settlement, a report). `3600`'s `:actuation/suppress-
alert` broke that pattern -- it WITHHOLDS/SILENCES an already-
triggered safety notification rather than issuing one. This actor's
`:actuation/suppress-billing-record` is the SECOND negative actuation
in this fleet's history, directly grounded in this blueprint's own
published Trust Control language: "billing records cannot be
suppressed without audit." The governed-actor discipline (HARD
checks, high-stakes gate, phase-3 exclusion, dedicated double-
actuation boolean) generalizes cleanly to this second negative
instance with no special-casing required -- the same architectural
validation `3600`'s ADR-0001 Decision 1 first proved, now confirmed a
second time on an unrelated domain (telecommunications billing vs.
water-safety alerting).

### Decision 2: entity and op shape

The primary entity is a `line` (a phone-number/line record, analogous
to `water.store`'s `site`). Five ops: `:line/intake` (directory
upsert, no capital risk), `:identity/verify` (per-jurisdiction
identity-verification evidence checklist, never auto -- analogous to
`water.operation`'s `:jurisdiction/assess`), `:billing/screen`
(billing-dispute screening, unconditional-evaluation discipline, never
auto), `:actuation/provision-number` (POSITIVE, high-stakes), and
`:actuation/suppress-billing-record` (NEGATIVE, high-stakes). This is
the SAME dual-actuation-on-one-entity shape `school`/`association`/
`leasing`/`behavioral`/`secondary`/`card`/`water` all use.

### Decision 3: `e164-invalid-format?` -- the FIRST format/syntactic-validity check family

`telecom.registry/e164-invalid-format?` independently recomputes
whether a line's own recorded E.164 number is a syntactically valid
number (leading `+`, no leading zero, 8-15 total digits). Before
writing this check's docstring, every prior sibling's `governor.cljc`
was grepped for `re-matches`/`re-find` (the mechanism a format check
would need) -- ZERO hits, confirming this is a genuinely new check
family, not a reuse. Every prior HARD check in this fleet's taxonomy
compares a MEASURED/RECORDED value against a range (`testlab`/
`conservation`/`water`'s two-sided range family), a threshold
(`facility`/`school`/`card`'s MAXIMUM-ceiling family; `association`/
`secondary`'s MINIMUM-floor family), a set (`registrar`/`casework`/
`secondary`'s subset family), a ratio (`leasing`/`behavioral`'s
quotient family), or an unresolved flag (the unconditional-evaluation
screening family) -- never the SYNTACTIC SHAPE of an identifier
itself. This verification-before-claiming-precedent step follows the
lesson `leasing`'s own ADR-0001 documents (a false-precedent claim was
caught and corrected there by reading the actual sibling source before
finalizing a docstring).

It is deliberately a SIMPLIFIED structural check, not a full ITU-T
E.164 numbering-plan validator (see `telecom.facts`'s docstring for
the same honest-scope discipline every sibling `facts` namespace
uses) -- but it is a genuine ground-truth recompute on the line's own
permanent field, independent of any advisor self-report, gating only
`:actuation/provision-number` (the point where a malformed number
would otherwise get provisioned for real use), the same restricted-
scope placement `water.governor`'s `contaminant-level-out-of-range-
violations` uses (gating only `:report/publish`, not the earlier
`:jurisdiction/assess`).

### Decision 4: `billing-dispute-unresolved-violations` -- the 26th unconditional-evaluation screening grounding

Following the discipline `casualty.governor/sanctions-violations`
established and twenty-five prior siblings (most recently `water.
governor/threshold-breach-unresolved-violations`, the 25th) have
applied, `billing-dispute-unresolved-violations` is evaluated
UNCONDITIONALLY -- not scoped to a specific op -- so `:billing/screen`
itself can HARD-hold on its own finding, not merely gate the
downstream actuation. This is the 26th distinct grounding of this
exact discipline, and the FIRST specifically for a billing-dispute
concept. Exercised in tests/demo via `:billing/screen` DIRECTLY
against an already-flagged line, not via an actuation op against an
unscreened line -- the "screen the screening op directly, not the
actuation op" lesson `parksafety`'s ADR-2607071922 Decision 5
established, now applied for a SIXTEENTH consecutive sibling
(`facility`=8th, `school`=9th, `association`=10th, `leasing`=11th,
`behavioral`=12th, `secondary`=13th, `card`=14th, `water`=15th,
`telecom`=16th).

### Decision 5: dedicated double-actuation-guard booleans

`:number-provisioned?`/`:billing-record-suppressed?` are dedicated
booleans on the `line` record, never a single `:status` value -- the
same discipline every prior sibling governor's guards establish,
informed by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`telecom.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed),
proven to satisfy the same contract in `test/telecom/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:line/intake` (no
capital risk). `:identity/verify` and `:billing/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and `:actuation/provision-number`/`:actuation/suppress-
billing-record` are permanently excluded from every phase's `:auto`
set -- a structural fact, not a rollout milestone, enforced by BOTH
`telecom.phase` and `telecom.governor`'s `high-stakes` set
independently.

### Decision 8: related-capability-contract-but-not-required posture

`telecom.*` cites `kotoba-lang/phone` (E.164 numbering, SIP URIs, CDR,
SMS) for the shape of a real number/CDR record without requiring it
directly -- the SAME posture `credit`/`leasing`/`card` established,
extended here to a genuinely telecom-specific capability contract. The
actor is fully self-contained and runs offline with `MemStore`; a
production deployment wires the real capability in as its number-
management/CDR backend.

### Decision 9: mock + LLM advisor pair

`telecom.telecomadvisor` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
provisioning a number or auto-suppressing a billing record).

### Decision 10: blueprint.edn field-sync fixes

Two stale-scaffold inconsistencies in `blueprint.edn`, discovered
during the standard "survey blueprint scaffold" step before writing
any code, were fixed as part of this promotion (the same class of
fix `card.6619`'s and `water.3600`'s own ADR-0001s document):

1. `:itonami.blueprint/id` was the stale pre-rename value
   `"cloud-itonami-6190"` (missing `isic-`), while the repo folder,
   README title and this actor's own `:business-id` already use the
   corrected `cloud-itonami-isic-6190`. Fixed to match.
2. `:itonami.blueprint/required-technologies` was missing `:robotics`
   (had only `[:phone :identity :forms :dmn :bpmn :audit-ledger]`)
   despite `blueprint.edn`'s own separate `:itonami.blueprint/robotics
   true` field AND the `kotoba-lang/industry` registry's own entry for
   `"6190"` already stating `[:robotics :phone :identity :forms :dmn
   :bpmn :audit-ledger]`. Fixed to match the registry exactly.

## Alternatives considered

- **A single actuation (provisioning only), treating billing-
  suppression as a lower-stakes administrative note.** Rejected: the
  blueprint's own Trust Controls explicitly name billing-record
  suppression as requiring governor approval and audit, on equal
  footing with number provisioning -- collapsing it into a non-high-
  stakes op would contradict the blueprint's own stated invariant.
- **Treating `e164-invalid-format?` as an extension of the existing
  two-sided range check family** (since both are pure ground-truth
  recomputes on a permanent field). Rejected after the grep-verified
  precedent check in Decision 3: a range check compares a value
  against bounds; a format check validates an identifier's syntax --
  different enough in shape and failure mode to warrant a distinct
  named family, avoiding an overly generic "ground-truth recompute"
  bucket that would obscure the taxonomy's usefulness for future
  actors choosing which check shape fits their own new concept.
- **Gating `e164-invalid-format?` at `:line/intake` as well as
  `:actuation/provision-number`.** Rejected to match `water.governor/
  contaminant-level-out-of-range-violations`'s precedent exactly: the
  advisor's intake proposal only normalizes/validates the patch
  fields it was given (never invents data), so a malformed number can
  be recorded at intake and is only HARD-blocked at the point it would
  actually be provisioned for real use -- consistent with every
  sibling's "the actor may hold bad data, but may never ACT on it
  uncaught" posture.

## Consequences

- Forty-second actor in this fleet (41 implemented before this build),
  and the SECOND infrastructure/utility vertical (after `3600`).
- Confirms the negative-actuation pattern generalizes across
  unrelated domains (water-safety alerting, telecom billing), not a
  one-off quirk of `3600`'s own domain.
- Establishes the first format/syntactic-validity check family,
  verified via grep to be a genuine addition to this fleet's check-
  family taxonomy, not a mis-attributed reuse.
- Two pre-existing `blueprint.edn` inconsistencies (stale ID, missing
  `:robotics` in required-technologies) fixed as in-scope minor
  consistency work, consistent with how `card.6619`/`water.3600`
  handled the same class of issue.
