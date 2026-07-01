# cloud-itonami-6190

Open Business Blueprint for **ISIC Rev.5 6190**: other telecommunications
activities (VoIP, public telephone and Internet access, telecom reselling,
specialized telecom applications).

This repository designs a forkable OSS business for community
telecommunications access: number provisioning, call routing, billing, and
support — run by a qualified operator so a community keeps its own call
records and numbering data.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a field-installer robot performs antenna splicing and last-mile line deployment under an actor that proposes
actions and an independent **Telecom Access Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Core Contract

```text
intake + identity + E.164 number assignment
        |
        v
Telecom Advisor -> Telecom Access Governor -> provision, bill, or human approval
        |
        v
CDR + SMS record + routing decision + audit ledger
```

No automated advice can provision a number, suppress a billing record, or
release a call/SMS without governor approval and audit evidence.

A live sample of the operator console is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of the kotoba-lang capability UI.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6190`). Required capabilities are implemented by:

- [`kotoba-lang/phone`](https://github.com/kotoba-lang/phone) — E.164 numbering, SIP URIs, CDR, SMS

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

Code and implementation templates are AGPL-3.0-or-later.
