(ns musicinstrmfg.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`musicinstrmfg.operation` -> `musicinstrmfg.governor` ->
  `musicinstrmfg.store`) through a scenario adapted from this repo's
  own `musicinstrmfg.sim` demo driver (`clojure -M:dev:run`, confirmed
  BEFORE writing this file to produce a sensible ledger against the
  real seeded batch/equipment ids `batch-001`/`batch-002`/`batch-003`/
  `woodshop-001`/`finishing-002` -- unlike `cloud-itonami-isic-851`'s
  `schoolops.sim`, this repo's own sim driver uses ids that DO match
  `musicinstrmfg.store/sample-data!`, so it was safe to reuse rather
  than author from scratch), trimmed to a representative subset (one
  full production-batch lifecycle spanning auto-commit + three
  escalate/approve ops, and four distinct HARD-hold reasons) and
  rendered deterministically -- no invented numbers, no timestamps in
  the page content, byte-identical across reruns against the same seed
  (verified by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [musicinstrmfg.store :as store]
            [musicinstrmfg.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :workshop-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: batch-001 clears a clean production-batch log
  (phase-3 auto-commit -- the only op ever auto-eligible in this
  domain), a maintenance-window scheduling against the verified,
  registered woodshop-001 bench (ALWAYS escalates -- schedule-
  maintenance is permanently excluded from every phase's :auto set --
  approved), a safety-concern flag against woodshop-001 (ALWAYS
  escalates -- high-stakes -- approved), and a shipment coordination
  against batch-001 within its own logged production quantity
  (phase-3: not yet auto-eligible -- escalates -- approved); then four
  independent HARD-hold scenarios that never reach a human:
  schedule-maintenance against the UNVERIFIED/unregistered
  finishing-002 station, coordinate-shipment against the
  UNVERIFIED/unregistered batch-003, coordinate-shipment against
  batch-002 for a quantity that would exceed its own recorded
  production quantity (60 units, 55 already shipped, +10 requested),
  and a schedule-maintenance attempt that declares
  `:actuate-equipment? true` against woodshop-001 (permanently blocked,
  independent of verification/registration status). Returns the
  resulting store -- every field read by `render` below is real
  governor/store output, not a hand-typed copy."
  []
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)]

    (exec! actor "t1" {:op :log-production-batch :effect :propose :subject "batch-001"
                        :patch {:instrument-family :strings :last-assessed "2026-07-19"}})

    (exec! actor "t2" {:op :schedule-maintenance :effect :propose :subject "mnt-1"
                        :value {:equipment-id "woodshop-001" :maintenance-type :blade-and-fence-inspection
                                :scheduled-date "2026-08-01" :actuate-equipment? false}})
    (approve! actor "t2")

    (exec! actor "t3" {:op :flag-safety-concern :effect :propose :subject "concern-1"
                        :value {:equipment-id "woodshop-001" :severity :moderate
                                :description "wood-dust collector filter saturation reading elevated"}})
    (approve! actor "t3")

    (exec! actor "t4" {:op :coordinate-shipment :effect :propose :subject "ship-1"
                        :value {:batch-id "batch-001" :units 50.0 :destination "buyer-retailer-north"}})
    (approve! actor "t4")

    (exec! actor "t5" {:op :schedule-maintenance :effect :propose :subject "mnt-2"
                        :value {:equipment-id "finishing-002" :maintenance-type :spray-booth-filter-check
                                :scheduled-date "2026-08-01" :actuate-equipment? false}})

    (exec! actor "t6" {:op :coordinate-shipment :effect :propose :subject "ship-2"
                        :value {:batch-id "batch-003" :units 20.0 :destination "buyer-retailer-south"}})

    (exec! actor "t7" {:op :coordinate-shipment :effect :propose :subject "ship-3"
                        :value {:batch-id "batch-002" :units 10.0 :destination "buyer-retailer-east"}})

    (exec! actor "t8" {:op :schedule-maintenance :effect :propose :subject "mnt-3"
                        :value {:equipment-id "woodshop-001" :maintenance-type :force-run
                                :scheduled-date "2026-09-01" :actuate-equipment? true}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- ready-cell [verified? registered?]
  (if (and verified? registered?)
    "<span class=\"ok\">verified &amp; registered</span>"
    "<span class=\"critical\">unverified/unregistered</span>"))

(defn- batch-row [ledger {:keys [id instrument-family lot-number quantity-units
                                  shipped-units verified? registered?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc (name (or instrument-family :n-a))) (esc lot-number)
          (esc quantity-units) (esc shipped-units)
          (ready-cell verified? registered?)
          (status-cell ledger id)))

(defn- equipment-row [ledger {:keys [id kind verified? registered?
                                      last-maintenance-date last-scheduled-maintenance-date]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc (name (or kind :n-a)))
          (ready-cell verified? registered?)
          (esc (or last-maintenance-date "n/a"))
          (esc (or last-scheduled-maintenance-date "none scheduled this run"))
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract (README
  ;; `Ops`, `musicinstrmfg.governor`/`musicinstrmfg.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-production-batch</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; instrument-family/pitch-accuracy/wood-moisture/defect-rate plausibility-checked</span></td></tr>"
   "        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase &middot; equipment independently re-verified &amp; re-registered &middot; direct actuation permanently blocked</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval (high-stakes) &middot; never gated on equipment verification status</span></td></tr>"
   "        <tr><td><code>:coordinate-shipment</code></td><td><span class=\"warn\">phase-3: human approval (not yet auto-eligible) &middot; batch independently re-verified &amp; re-registered &middot; shipment quantity independently recomputed against the batch's own logged production quantity</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        batches (store/all-batches db)
        equipment (store/all-equipment db)
        batch-rows (str/join "\n" (map (partial batch-row ledger) batches))
        equipment-rows (str/join "\n" (map (partial equipment-row ledger) equipment))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-3220 &middot; musical-instrument manufacturing operations</title><style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Musical instrument manufacturing operations (ISIC 3220) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · maintenance scheduling &amp; equipment actuation always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Production batches</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>musicinstrmfg.store</code> via <code>musicinstrmfg.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly. \"Last op status\" reflects only requests whose own <code>:subject</code> is the batch id itself (production-batch logging); maintenance/safety/shipment requests file under their own record id — see the audit ledger below for the full picture.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Instrument family</th><th>Lot</th><th>Quantity (units)</th><th>Shipped (units, after this run)</th><th>Verification</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     batch-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Crafting/assembly equipment</h2>\n"
     "    <p class=\"muted\">\"Newly scheduled maintenance\" is a real derived field — it only changes when a <code>:schedule-maintenance</code> proposal actually commits (via <code>musicinstrmfg.store</code>'s <code>:maintenance/schedule</code> effect), so it shows the concrete effect of this run's HARD holds vs. its one approved schedule.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Equipment</th><th>Kind</th><th>Verification</th><th>Last maintenance (on record)</th><th>Newly scheduled maintenance (this run)</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     equipment-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Musical Instrument Workshop Plant Operations Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Equipment/batch verification is independently re-checked, never trusted from the proposal; shipment quantities are independently recomputed against the batch's own logged production quantity; direct crafting/assembly-equipment actuation is permanently blocked regardless of confidence or approval.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/maintenance-history db)) "maintenance schedules,"
             (count (store/shipment-history db)) "shipment coordinations )")))
