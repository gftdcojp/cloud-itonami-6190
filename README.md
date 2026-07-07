# cloud-itonami-isic-6190

Open Business Blueprint for **ISIC Rev.5 6190**: other telecommunications
activities (VoIP, public telephone and Internet access, telecom reselling,
specialized telecom applications).

This repository publishes a telecom-operator actor -- line intake, identity
verification, billing-dispute screening, number provisioning and billing-
record suppression -- as an OSS business that any qualified community
telecom operator can fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600)) --
the SECOND infrastructure/utility vertical in this fleet (after `3600`'s
water-safety operations). Here it is **Telecom Advisor ⊣ Telecom Access
Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a line-
> intake summary, normalizing records, and checking whether a line's
> own recorded E.164 number is even syntactically well-formed -- but
> it has **no notion of which jurisdiction's numbering-plan
> requirements are official, no license to provision a real E.164
> number or suppress a real billing record, and no way to know on its
> own whether a billing dispute against the line has actually stayed
> unresolved**. Letting it provision a number or suppress a billing
> record directly invites fabricated numbering-plan citations, a
> number provisioned on a malformed E.164 string, and an unresolved
> billing dispute being quietly silenced -- and liability, and
> consumer-protection risk, for whoever runs it. This project seals
> the Telecom Advisor into a single node and wraps it with an
> independent **Telecom Access Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers line intake through identity verification,
billing-dispute screening, number provisioning and billing-record
suppression. It does **not**, by itself, hold any license required to
operate a telecommunications service in a given jurisdiction, and it
does not claim to. It also does **not** model a real SIP/softswitch
routing engine, a real HLR/number-portability database, or lawful-
intercept infrastructure -- no call-routing dispatch, no real-time
CDR/SMS ingestion pipeline (see `telecom.facts`'s own docstring for
the honest simplification this makes: a starting catalog of numbering-
plan authorities, not a survey of every jurisdiction's numbering-plan
variant). Whoever deploys and operates a live instance (a licensed
telecom operator) supplies any jurisdiction-specific license, the real
switching/routing infrastructure and the real lawful-intercept/
emergency-path integrations, and bears that jurisdiction's liability
-- the software supplies the governed, spec-cited, audited execution
scaffold so that operator does not have to build the compliance layer
from scratch for every new market.

### Actuation

**Provisioning a real E.164 number or suppressing a real billing
record is never autonomous, at any phase, by construction.** Two
independent layers enforce this (`telecom.governor`'s `:actuation/
provision-number`/`:actuation/suppress-billing-record` high-stakes
gate and `telecom.phase`'s phase table, which never puts `:actuation/
provision-number`/`:actuation/suppress-billing-record` in any phase's
`:auto` set) -- see `telecom.phase`'s docstring and `test/telecom/
phase_test.clj`'s `provision-number-never-auto-at-any-phase`/
`suppress-billing-record-never-auto-at-any-phase`. The actor may
draft, check and recommend; a human telecom operator is always the
one who actually provisions a number or suppresses a billing record.
Like `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/
`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/`8720`/`8521`/
`6619`/`3600`, this actor has TWO actuation events -- and like `3600`,
**`:actuation/suppress-billing-record` is a NEGATIVE actuation**: it
withholds/silences a billing record rather than issuing one -- the
SECOND time this fleet has modeled a high-stakes act in that
direction. See this actor's own `docs/adr/0001-architecture.md`
Decision 1 for the honest framing this makes.

## The core contract

```
line intake + jurisdiction facts (telecom.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Telecom      │ ─────────────▶ │ Telecom                       │  (independent system)
   │ Advisor      │  + citations    │ Access Governor:              │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ E.164-format-
                           record + ledger  escalate ─▶ human   invalid (structural) ·
                                             (ALWAYS for         billing-dispute-
                                              :actuation/provision-     unresolved (unconditional) ·
                                              number /                 already-provisioned/-suppressed
                                              :actuation/suppress-
                                              billing-record)
```

**The Telecom Advisor never provisions a number or suppresses a
billing record the Telecom Access Governor would reject, and never
does so without a human sign-off.** Hard violations (fabricated
numbering-plan requirements; unsupported evidence; a malformed E.164
number; an unresolved billing dispute; a double provisioning or
suppression) force **hold** and *cannot* be approved past; a clean
provisioning/suppression proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

A live sample of the operator console is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html)
-- pure-data HTML output of the kotoba-lang capability UI.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a field-installer robot
performs antenna splicing and last-mile line deployment at network
sites, under the actor, gated by the independent **Telecom Access
Governor**. The governor never dispatches hardware itself; `:high`/
`:safety-critical` actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Telecom Access Governor, number-provisioning + billing-suppression draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6190`). Required capabilities are implemented by:

- [`kotoba-lang/phone`](https://github.com/kotoba-lang/phone) -- E.164 numbering, SIP URIs, CDR, SMS

`telecom.*` cites this capability contract for the shape of a real
E.164 number/SIP URI/CDR record, the same "related capability contract
but not required" posture `credit.governor`/`leasing.governor`/
`card.governor` established -- the actor is self-contained and runs
without `kotoba-lang/phone` installed; a production deployment wires
the real capability in as its number-management/CDR backend.

## Layout

| File | Role |
|---|---|
| `src/telecom/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate number-provisioning/billing-suppression history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded line, and the double-actuation guards check dedicated `:number-provisioned?`/`:billing-record-suppressed?` booleans rather than a `:status` value |
| `src/telecom/registry.cljc` | Number-provisioning + billing-suppression draft records, plus `e164-invalid-format?` -- the FIRST instance of this fleet's format/syntactic-validity check family (grep-verified absent from every prior sibling's `governor.cljc` before this docstring was written) |
| `src/telecom/facts.cljc` | Per-jurisdiction telecommunications-numbering-plan catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/telecom/telecomadvisor.cljc` | **Telecom Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/verification/billing-dispute-screening/number-provisioning/billing-suppression proposals |
| `src/telecom/governor.cljc` | **Telecom Access Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · E.164-format-invalid, pure ground-truth structural recompute · billing-dispute-unresolved, unconditional evaluation, the TWENTY-SIXTH grounding of this discipline and FIRST specifically for the billing-dispute concept) + already-provisioned/already-suppressed guards + 1 soft (confidence/actuation gate) |
| `src/telecom/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both number provisioning and billing-record suppression always human; line intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/telecom/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/telecom/sim.cljc` | demo driver |
| `test/telecom/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers line intake through identity verification, billing-
dispute screening, number provisioning and billing-record suppression
-- the core governed lifecycle this blueprint's own `docs/business-
model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Line intake + per-jurisdiction numbering-plan checklisting, HARD-gated on an official spec-basis citation (`:line/intake`/`:identity/verify`) | Real SIP/softswitch call routing, real HLR/number-portability integration (see `telecom.facts`'s docstring) |
| Billing-dispute screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:billing/screen`) | Real CDR/SMS-record ingestion pipeline |
| Number provisioning, HARD-gated on full evidence and E.164 structural validity, plus a double-provisioning guard (`:actuation/provision-number`) | Lawful-intercept and emergency-path infrastructure (deliberately outside LLM/actor control) |
| Billing-record suppression, HARD-gated on full evidence and a double-suppression guard (`:actuation/suppress-billing-record`) | |
| Immutable audit ledger for every intake/verification/screening/provisioning/suppression decision | |

Extending coverage is additive: add the next gate (e.g. a number-
porting-request check) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern
this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`telecom.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `telecom.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `telecom.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `Telecom Advisor` + `Telecom Access Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the forty-one
prior actors' architecture. See `docs/adr/0001-architecture.md` for
the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
