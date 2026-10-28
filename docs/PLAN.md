# TaskTrackerKMP — Active roadmap

This is the concise index of active and completed roadmap areas. A task spec is the implementation
contract; when an active item has no spec, consult only its section in the
archive and create a spec before implementation.

## How to use this roadmap

- Choose an active item below and respect its dependencies and stated order.
- Read its spec in `specs/` if one exists. Create and review a spec
  before implementation.
- Keep one task on one branch and one PR. Do not bundle neighboring work.
- Follow `AGENTS.md` for ACK, host, test-review, validation, and merge rules.
- Detailed test-harness cleanup has its own linked plan; don't duplicate it here.

## 10 — Mobile LAN parity

Mobile clients reach the plain-HTTP `lan` provider like desktop: the platform
transport gates (Android's cleartext allow-list, iOS ATS plus the local-network
prompt) are lifted above the unchanged 6.3 validator policy, and the pending
`android-local-network-permission` acceptance completes with it.

| Task | State | Intent | Depends on | Execution details |
| --- | --- | --- | --- | --- |
| 10.1 | In progress | Mobile plain-HTTP LAN parity | android-local-network-permission | [`specs/mobile-lan-plain-http.md`](../specs/mobile-lan-plain-http.md) |
