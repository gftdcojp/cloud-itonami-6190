# Business Model: Community Telecommunications Access

## Classification

- Repository: `cloud-itonami-isic-6190`
- ISIC Rev.5: `6190`
- Activity: other telecommunications activities — VoIP, public access, reselling
- Social impact: connectivity, digital inclusion, resilient local infrastructure

## Customer

- rural and community network operators
- municipalities running public-access telephone and Internet points
- VoIP and reseller operators
- disaster-preparedness and emergency-communications groups
- cooperatives that cannot accept closed telecom-platform lock-in

## Offer

- E.164 number provisioning and porting
- call routing and SIP endpoint management
- call detail record (CDR) and SMS record management
- usage-based billing workflows
- support and number-directory services
- role-based access and purpose limitation
- immutable audit ledger
- migration and managed operations

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per operator
- support: monthly retainer with SLA
- number provisioning and porting services
- billing and settlement integration

## Trust Controls

- numbers cannot be provisioned without identity and governor approval
- billing records cannot be suppressed without audit
- lawful-intercept and emergency paths remain outside LLM control
- every provision, route, bill and disclose path is auditable
- customer traffic and location data stays outside Git
- a fabricated numbering-plan citation, incomplete evidence, a
  malformed E.164 number, or an unresolved billing dispute -- each
  forces a hold, not an override
- billing-record suppression is logged and escalated, and cannot be
  finalized twice for the same line: a double-suppression attempt is
  held off this actor's own line facts alone, with no upstream
  comparison needed
