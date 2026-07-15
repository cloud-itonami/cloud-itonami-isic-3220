# ADR-0001: InstrumentAdvisor ⊣ Musical Instrument Workshop Plant Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-3220` promoted from `:spec` to
`:implemented` in the `kotoba-lang/industry` registry, following the
verified fresh-scaffold protocol established by prior actors in this
fleet.

## Context

`cloud-itonami-isic-3220` publishes an OSS blueprint for musical-
instrument-workshop **plant operations coordination** (production-
batch instrument-family/pitch-accuracy/wood-moisture/defect-rate data
logging, crafting/assembly-equipment maintenance scheduling, safety-
concern flagging, and outbound product shipment coordination). Like
every actor in this fleet, the blueprint alone is not an
implementation: this ADR records the governed-actor architecture that
promotes it to real, tested code, following the same langgraph
StateGraph + independent Governor + Phase 0->3 rollout pattern
established across the cloud-itonami fleet.

Identity ({:id "3220" :name "Manufacture of musical instruments"}) was
independently verified against a fresh clone of `kotoba-lang/
industry`'s `resources/kotoba/industry/registry.edn` before any work
began, per this fleet's ID/name-mismatch caution (prior agents in this
fleet have mislabeled their assigned ISIC class). The entry's own
`:repo` field pointed at a stale, never-created `gftdcojp/cloud-
itonami-C3220` placeholder; the real `cloud-itonami` org target name
was independently confirmed 404 via `gh api repos/cloud-itonami/
cloud-itonami-isic-3220` before scaffolding began.

The closest domain analog is `cloud-itonami-isic-3211` (Manufacture of
jewellery and related articles): both are back-office coordination
actors for a fixed processing PLANT/WORKSHOP with precision equipment
and a real safety/consumer-protection dimension, and both share the
same four-op shape (`:log-production-batch`/`:schedule-maintenance`/
`:flag-safety-concern`/`:coordinate-shipment`) and the same two-entity
verified/registered gate structure (equipment for maintenance
scheduling, batch for shipment coordination). This build mirrors
`cloud-itonami-isic-3211`'s architecture closely but adapts the hazard
profile and equipment/product vocabulary to the musical-instrument
workshop: this vertical's central physical hazard is crafting/
assembly-equipment operation (woodworking benches, CNC routers,
buffing/finishing stations) and materials-safety (wood-dust and
finish-chemical) exposure, rather than 3211's solvent/acid-pickling
and theft/security-fraud dimension; its permanent equipment-actuation
block guards crafting/assembly EQUIPMENT (`:actuate-equipment?`)
rather than casting/setting/polishing EQUIPMENT; its production-batch
record declares an `:instrument-family` (closed set spanning strings/
wind/percussion/keyboard) and a `:pitch-accuracy-cents` reading (this
vertical's own tonal-test plausibility check, symmetric +/-50-cent
chromatic-tuning-meter window) and a `:wood-moisture-content-percent`
reading (this vertical's own genuinely new physical check,
plausibility-checked above 0% up to a 30% fiber-saturation-point
ceiling -- tracking the batch's tonewood moisture content, a
domain-specific data point 3211 has no analog for) in addition to a
`:defect-rate-percent`, rather than 3211's `:metal-type`/`:purity-
permille`/`:weight-grams`; and its shipment quantity is tracked in
finished-instrument UNITS (`:units`/`:quantity-units`/`:shipped-
units`), the same counted-not-weighed shape 3211 uses for finished
jewellery pieces.

Unlike 3211, this vertical has NO domain-specific permanent-
certification-authority block: musical-instrument manufacture has no
single, universally recognized accredited certification authority
analogous to a hallmarking/purity-assay office, so no
`:issue-*-certification?` HARD check is elaborated here (the closed
proposal-effect allowlist already blocks any effect outside the four
propose-shaped drafts, which is the equipment-control boundary this
vertical actually needs).

This vertical has NO pre-existing `kotoba-lang/musicinstrmfg`-style
capability library to wrap (verified: no such repo exists, and no
`music`/`instrument`-named manufacturing-capability repo exists in
`kotoba-lang` either, via GitHub code/repo search). This build
therefore uses self-contained domain logic -- pure functions in
`musicinstrmfg.registry` (equipment/batch verification, shipment-
quantity recompute, instrument-family validation, pitch-accuracy
plausibility validation, wood-moisture-content plausibility
validation, defect-rate plausibility validation) are re-verified
independently by the governor, the same "ground truth, not
self-report" discipline established across prior actors (most
directly `cloud-itonami-isic-3211`'s `jewellerymfg.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:musical-instrument-workshop-plant-operations-governor`, is
grep-verified UNIQUE fleet-wide (`gh search code
"musical-instrument-workshop-plant-operations-governor" --owner
cloud-itonami`, zero hits before this repo was created).

## Decision

### Decision 1: Self-contained domain logic (no external musical-instrument-manufacturing capability library to wrap)

Unlike actors that delegate to pre-existing domain libraries, this
musical-instrument-workshop vertical has NO pre-existing capability
library to wrap. The equipment/batch-verification / shipment-quantity
/ instrument-family / pitch-accuracy / wood-moisture-content /
defect-rate validation functions live as pure functions in
`musicinstrmfg.registry` and are re-verified independently by
`musicinstrmfg.governor` -- the same "ground truth, not self-report"
discipline established across prior actors (most directly
`cloud-itonami-isic-3211`'s `jewellerymfg.registry`).

### Decision 2: Coordination, not control — scope boundary at the back-office

This actor is **strictly back-office coordination** of musical-
instrument-workshop plant operations. It does NOT:
- Control crafting or assembly equipment directly
- Make workshop-safety or equipment-actuation decisions (exclusive to the human workshop supervisor)
- Actuate crafting/assembly equipment

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human workshop-
supervisor approval. This is not a replacement for the supervisor's
authority — it is a proposal-screening and documentation layer.

**CRITICAL SAFETY BOUNDARY**: musical-instrument manufacturing is a
safety-relevant domain (wood-dust/finish-chemical materials-safety
hazard, consumer-protection consequence downstream). Safety-concern
flagging NEVER auto-commits. All safety concerns escalate immediately
to human review.

### Decision 3: Safety-concern escalation — always human sign-off

`:flag-safety-concern` (materials-safety wood-dust/finish-chemical
concern, equipment-safety concern) ALWAYS escalates, never
auto-commits. This is not a "low-stakes proposal" — it is a
circuit-breaker that must reach human authority.

### Decision 4: Two independent verified/registered gates (equipment AND batch), not one

Like `cloud-itonami-isic-3211`, this vertical has TWO entity kinds
each gating a different op: `:schedule-maintenance` independently
verifies the referenced **equipment** unit's own `:verified?`/
`:registered?` fields; `:coordinate-shipment` independently verifies
the referenced **batch**'s own `:verified?`/`:registered?` fields.
Both are the same "workshop/batch record must be independently
verified/registered before any action" HARD invariant applied to the
two distinct record kinds this domain actually has.
`:coordinate-shipment` additionally independently recomputes whether
a batch's own recorded shipped-to-date unit quantity plus the
proposal's own claimed unit quantity would exceed the batch's own
recorded production quantity — never taken on the advisor's
self-report.

### Decision 5: HARD invariants (no override)

Four HARD governor invariants (elaborated into twelve concrete checks
in `musicinstrmfg.governor`, mirroring `cloud-itonami-isic-3211`'s
own elaboration of its HARD invariants into concrete checks, minus the
hallmark/purity-assay-authority check this vertical has no analog for)
block proposals and cannot be overridden by human approval:
1. Workshop/batch record (equipment for maintenance, batch for shipment) must be independently verified/registered before any action is taken against it, and a shipment's quantity must independently recompute within the batch's own logged production quantity
2. Proposals must be `:effect :propose` only (never direct equipment control)
3. Direct crafting/assembly-equipment control or equipment actuation is permanently blocked
4. The op allowlist is closed — `:log-production-batch`/`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` only

## Consequences

(+) Musical-instrument-workshop plant operations back-office now has a
documented, governed, auditable coordination layer that funnels all
decisions through independent validation before human approval.

(+) The "coordination, not control" boundary is explicit in code: all
`:effect :propose`, all real-world actuation requires human workshop-
supervisor sign-off.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into twelve concrete governor checks) protect against scope creep into
unauthorized equipment operation or equipment actuation. Safety
concerns are a circuit-breaker, not a threshold.

(+) Safety-critical discipline is explicit: safety-concern flagging
cannot be rate-limited, suppressed, or auto-decided by phase gate.
Human review is mandatory.

(-) Still a simulation/proposal layer, not a real workshop-operations
control system. Equipment actuation and line operation remain human-
controlled via external channels.

(-) No integration with real workshop-management databases (equipment
telemetry, batch tracking, freight dispatch) — this is a standalone
coordinator blueprint.

## Verification

- `cloud-itonami-isic-3220`: `clojure -M:test` green (all tests pass;
  see the superproject ADR and `kotoba-lang/industry` registry entry
  for the exact `Ran N tests containing M assertions, 0 failures, 0
  errors` output, verified from an independent fresh clone), `clojure
  -M:lint` clean, `clojure -M:dev:run` demo narrative exercises
  proposal submission, escalation, and every HARD-hold scenario
  directly (not-propose-effect, unknown-op, equipment-not-verified,
  batch-not-verified, shipment-quantity-exceeded, equipment-actuate-
  blocked, already-scheduled, invalid-instrument-family,
  invalid-pitch-accuracy, invalid-wood-moisture,
  invalid-defect-rate).
- All source is `.cljc` (portable ClojureScript / JVM / nbb) — no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `clojure -M:test` resolves offline inside the monorepo checkout.
