# cloud-itonami-isic-3220: Manufacture of musical instruments

Open Business Blueprint for **ISIC 3220**: manufacture of musical instruments — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office **musical-instrument-workshop plant operations**: production-batch data logging (instrument-family/pitch-accuracy/wood-moisture/defect-rate), crafting/assembly-equipment maintenance scheduling, safety-concern flagging, and outbound product shipment coordination.

This repository designs a forkable OSS business for musical-instrument-
workshop plant operations: run by a qualified operator so a workshop
keeps its own operating records instead of renting a closed SaaS.

## Scope: plant operations coordination, not workshop-line control

ISIC 3220 covers the **musical-instrument workshop** that crafts
(bodies/necks/soundboards/keywork/shells/frames), assembles, and
tonal-tests the resulting finished stringed, wind, percussion, and
keyboard instruments. This actor coordinates the back-office record
keeping around that workshop — it never touches the crafting/assembly
equipment directly.

## What this actor does

Proposes **plant operations coordination**, not equipment operation:
- `:log-production-batch` — crafting/assembly/tonal-test batch, output-quality data logging (administrative, not an operational decision)
- `:schedule-maintenance` — crafting/assembly-equipment maintenance scheduling proposal
- `:flag-safety-concern` — surface a materials-safety (wood-dust/finish-chemical)/equipment-safety concern (always escalates)
- `:coordinate-shipment` — outbound product shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY — this is a safety-relevant domain**
(crafting/assembly-line equipment, wood-dust/finish-chemical materials
hazard, consumer-protection consequence downstream):

- Does NOT control crafting or assembly equipment directly
- Does NOT make workshop-safety or equipment-actuation decisions (that's the workshop supervisor's exclusive human authority)
- Does NOT actuate crafting/assembly equipment (human workshop supervisor decides)
- ONLY proposes/coordinates operations back-office; all actuation requires explicit human authority
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation

## Architecture

Classic governed-actor pattern (`musicinstrmfg.operation/build`, a langgraph-clj StateGraph):
1. **`musicinstrmfg.advisor`** (sealed intelligence node, `InstrumentAdvisor`): proposes decisions only, never commits
2. **`musicinstrmfg.governor`** (independent, `Musical Instrument Workshop Plant Operations Governor`): validates against domain rules, re-derived from `musicinstrmfg.registry`'s pure functions and `musicinstrmfg.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Workshop/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects (no direct crafting/assembly-line-equipment control)
     - Directly actuating crafting/assembly equipment (`:actuate-equipment? true`) is a PERMANENT, unconditional block
     - A shipment may not push a batch's own recorded shipped quantity past its own logged production quantity (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:instrument-family` value on a production-batch patch
     - No physically implausible `:pitch-accuracy-cents` value on a production-batch patch
     - No physically implausible `:wood-moisture-content-percent` value on a production-batch patch
     - No physically implausible `:defect-rate-percent` value on a production-batch patch
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`musicinstrmfg.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`musicinstrmfg.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
kbb -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
kbb -M:dev:test

# Run the demo
kbb -M:dev:run

# Lint
kbb -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
