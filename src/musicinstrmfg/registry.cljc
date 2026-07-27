(ns musicinstrmfg.registry
  "Pure-function domain logic for the musical-instrument-workshop
  plant-operations coordination actor -- equipment/batch verification,
  shipment-quantity recompute, instrument-family validation,
  pitch-accuracy plausibility validation, wood-moisture-content
  plausibility validation, defect-rate plausibility validation, and
  draft maintenance-schedule/shipment-coordination record
  construction.

  Per docs/adr/0001-architecture.md Decision 1: this vertical has NO
  pre-existing `kotoba-lang/musicinstrmfg`-style capability library to
  wrap (verified: no such repo exists, and no `music`/`instrument`-
  named manufacturing-capability repo exists in kotoba-lang either,
  via GitHub code/repo search). The domain logic therefore lives here
  as pure functions, re-verified INDEPENDENTLY by
  `musicinstrmfg.governor` -- the same 'ground truth, not self-report'
  discipline every sibling actor's own registry establishes (most
  directly `jewellerymfg.registry` from `cloud-itonami-isic-3211`,
  this vertical's closest domain analog): never trust a proposal's own
  self-reported quantity/status when the inputs needed to recompute it
  independently are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real workshop-operations system. It builds the DRAFT
  record a workshop coordinator would keep (a scheduled equipment
  maintenance window, a coordinated shipment), not the act of
  actuating crafting/assembly-line equipment or dispatching a real
  freight carrier (this actor NEVER does either -- see README `What
  this actor does NOT do`).

  SCOPE: ISIC 3220 covers manufacture of musical instruments --
  woodworking (bodies/necks/soundboards), metalworking (brass/
  woodwind keywork, percussion shells/hardware, piano frames/strings),
  assembly, and tonal-test lines producing finished stringed, wind,
  percussion, and keyboard instruments. This actor coordinates the
  back-office record-keeping around that workshop (production-batch
  logging, maintenance scheduling, safety-concern flagging, shipment
  coordination) -- it never touches the crafting/assembly equipment
  directly.")

;; ----------------------------- constants -----------------------------

(def valid-instrument-families
  "The closed set of instrument-family values a production-batch
  record may declare -- the instrument categories routinely crafted/
  assembled/tonal-tested in a musical-instrument workshop. Anything
  else is a fabricated/unrecognized instrument family -- the governor
  HARD-holds rather than let an invented classification pass through.
  This actor never DECIDES a batch's instrument family (that is the
  workshop's own production-intake function); it only validates that a
  batch record declares one of the real, known values."
  #{:strings :wind :percussion :keyboard})

(def pitch-accuracy-cents-min
  "Physical floor for a batch's own tonal-test pitch-accuracy reading,
  expressed in cents (1/100 of a semitone) of deviation from standard
  pitch. A chromatic tuning meter reads at most a half-semitone
  (+/-50 cents) deviation before the reading would register as a
  different pitch class entirely -- a reading beyond this floor is not
  a real tuning-meter measurement."
  -50)

(def pitch-accuracy-cents-max
  "Physical ceiling for a batch's own tonal-test pitch-accuracy
  reading in cents -- symmetric with `pitch-accuracy-cents-min` (a
  chromatic tuning meter's own +/-50-cent measurement window)."
  50)

(def wood-moisture-content-percent-min
  "Physical floor for a batch's own tonewood moisture-content reading
  (percent, oven-dry basis) -- a batch must have strictly positive
  moisture content; zero/negative moisture content is not a real
  cured-wood reading."
  0.0)

(def wood-moisture-content-percent-max
  "Physical ceiling for a batch's own tonewood moisture-content
  reading in percent -- 30% approximates the fiber-saturation point
  for most tonewood species (the moisture level above which wood cell
  walls are fully saturated and further water sits only in cell
  cavities); a reading beyond this is implausible sensor data, not a
  real cured-tonewood batch ready for crafting."
  30.0)

(def defect-rate-min-percent
  "Physical floor for a batch's own crafting/assembly/tonal-test
  defect-rate reading (zero defects is the best possible outcome,
  never negative)."
  0.0)

(def defect-rate-max-percent
  "Physical ceiling for a batch's own defect-rate reading -- a batch
  cannot reject more than 100% of its own output. A reading above this
  is implausible sensor/QC data, not a real batch."
  100.0)

;; ----------------------------- equipment checks -----------------------------

(defn equipment-verified?
  "Ground-truth check: has `equipment`'s own record been marked
  verified (i.e. it has actually been inspected/commissioned and
  registered in the SSoT, not merely referenced from an unverified
  maintenance request)? A pure predicate over the equipment's own
  permanent field -- no proposal inspection needed."
  [equipment]
  (true? (:verified? equipment)))

(defn equipment-registered?
  "Ground-truth check: does `equipment`'s own record carry a
  `:registered?` true flag (i.e. it is on file in the workshop's
  equipment registry)? Scheduling maintenance against equipment that
  is not on file and registered is the exact scope violation this
  actor's HARD invariant ('workshop/batch record must be
  independently verified/registered before any action') exists to
  block."
  [equipment]
  (true? (:registered? equipment)))

(defn equipment-ready?
  "Combined ground-truth gate: the equipment must be both `verified?`
  AND `registered?` before ANY maintenance may be scheduled against
  it. Two independent facts on the equipment's own permanent record,
  neither inferred from the advisor's own rationale."
  [equipment]
  (and (equipment-verified? equipment) (equipment-registered? equipment)))

;; ----------------------------- batch checks -----------------------------

(defn batch-verified?
  "Ground-truth check: has `batch`'s own record been marked verified
  (i.e. its instrument-family/pitch-accuracy/wood-moisture/defect-rate
  claims have actually been QC-inspected, not merely logged from an
  unverified intake patch)?"
  [batch]
  (true? (:verified? batch)))

(defn batch-registered?
  "Ground-truth check: is `batch`'s own record on file in the
  workshop's production ledger? Coordinating a shipment against a
  batch that is not on file and registered is the exact scope
  violation this actor's HARD invariant ('workshop/batch record must
  be independently verified/registered before any action') exists to
  block."
  [batch]
  (true? (:registered? batch)))

(defn batch-ready?
  "Combined ground-truth gate: the batch must be both `verified?` AND
  `registered?` before ANY shipment may be coordinated against it."
  [batch]
  (and (batch-verified? batch) (batch-registered? batch)))

(defn shipment-quantity-exceeded?
  "Ground-truth check for a `:coordinate-shipment` proposal:
  would `shipped-units` + `new-units` exceed `batch`'s own recorded
  `:quantity-units` (the batch's own logged production quantity)?
  Needs no proposal inspection or stored-verdict lookup -- its inputs
  are permanent fields already on the batch's own record, the same
  shape every sibling actor's own cost/total-matching check uses."
  [batch new-units]
  (let [capacity (:quantity-units batch)
        so-far (:shipped-units batch 0.0)]
    (and (number? capacity)
         (number? new-units)
         (number? so-far)
         ;; Compared at 1/10000 of a unit, not on raw doubles. A shipment
         ;; that fills a batch EXACTLY to its recorded capacity is legal,
         ;; and comparing the raw sum flagged such shipments as over
         ;; because the sum is not the double nearest the true total.
         (> (Math/round (* 10000 (+ (double so-far) (double new-units))))
            (Math/round (* 10000 (double capacity))))))) 

(defn shipment-quantity-exceeded-checkable?
  "Can `batch`'s headroom actually be computed for `new-units`?

  `shipment-quantity-exceeded?` answers only `over` / `not over`, and its
  `(and (number? ...) ...)` guard made every un-checkable case fall
  through as `not over` -- a batch with no recorded capacity, or a
  shipment stating no amount, passed the over-capacity check silently.
  Callers must ask this first: un-checkable is not headroom."
  [batch new-units]
  (boolean (and (map? batch)
                (number? (:quantity-units batch))
                (number? (:shipped-units batch 0.0))
                (number? new-units))))

(defn instrument-family-valid?
  "Is `instrument-family` one of the closed, known instrument-family
  values? nil/blank is treated as invalid (a production-batch patch
  must declare a real instrument family, not omit it silently)."
  [instrument-family]
  (contains? valid-instrument-families instrument-family))

(defn pitch-accuracy-cents-valid?
  "Is `cents` a physically plausible tonal-test pitch-accuracy
  reading, expressed in cents of deviation from standard pitch?
  Rejects nil, non-integers, values below
  `pitch-accuracy-cents-min`, and values beyond
  `pitch-accuracy-cents-max` -- a fabricated or tuning-meter-error
  reading, never let through as a real tonal-test fact."
  [cents]
  (and (integer? cents)
       (>= cents pitch-accuracy-cents-min)
       (<= cents pitch-accuracy-cents-max)))

(defn wood-moisture-content-percent-valid?
  "Is `percent` a physically plausible tonewood moisture-content
  reading, in percent? Rejects nil, non-numbers, values at or below
  `wood-moisture-content-percent-min`, and values beyond
  `wood-moisture-content-percent-max` -- a fabricated or
  sensor-error reading, never let through as a real batch fact."
  [percent]
  (and (number? percent)
       (> (double percent) wood-moisture-content-percent-min)
       (<= (double percent) wood-moisture-content-percent-max)))

(defn defect-rate-valid?
  "Is `percent` a physically plausible batch crafting/assembly/tonal-
  test defect-rate reading? Rejects nil, non-numbers, negative values,
  and values beyond `defect-rate-max-percent` -- a fabricated or
  sensor-error reading, never let through as a real batch fact."
  [percent]
  (and (number? percent)
       (>= (double percent) defect-rate-min-percent)
       (<= (double percent) defect-rate-max-percent)))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human workshop supervisor's/shipping approver's act, not this
  actor's."
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

(defn register-maintenance
  "Validate + construct the MAINTENANCE-SCHEDULE DRAFT -- a proposed
  crafting/assembly-equipment maintenance window against a verified,
  registered piece of equipment. Pure function -- does not actuate the
  crafting/assembly equipment or execute any maintenance; it builds
  the RECORD a workshop coordinator would keep. `musicinstrmfg.governor`
  independently re-verifies the equipment's own verified/registered
  ground truth, and permanently blocks any attempt to directly actuate
  crafting/assembly equipment (see README `Actuation`), before this is
  ever allowed to commit."
  [maintenance-id equipment-id sequence]
  (when-not (and maintenance-id (not= maintenance-id ""))
    (throw (ex-info "maintenance: maintenance_id required" {})))
  (when-not (and equipment-id (not= equipment-id ""))
    (throw (ex-info "maintenance: equipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "maintenance: sequence must be >= 0" {})))
  (let [maintenance-number (str "MNT-" (zero-pad sequence 6))
        record {"record_id" maintenance-number
                "kind" "maintenance-schedule-draft"
                "maintenance_id" maintenance-id
                "equipment_id" equipment-id
                "immutable" true}]
    {"record" record "maintenance_number" maintenance-number
     "certificate" (unsigned-certificate "MaintenanceSchedule" maintenance-number maintenance-number)}))

(defn register-shipment
  "Validate + construct the SHIPMENT-COORDINATION DRAFT -- a proposed
  outbound instrument shipment against a verified, registered
  production batch. Pure function -- does not dispatch any real
  freight carrier; it builds the RECORD a workshop coordinator would
  keep. `musicinstrmfg.governor` independently re-verifies the
  shipment's own claimed quantity against `shipment-quantity-
  exceeded?`, before this is ever allowed to commit."
  [shipment-id sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment: shipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str "SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-coordination-draft"
                "shipment_id" shipment-id
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentCoordination" shipment-number shipment-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
