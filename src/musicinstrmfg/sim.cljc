(ns musicinstrmfg.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean workshop through
  intake -> maintenance scheduling (escalate/approve) -> safety-concern
  flag (escalate/approve) -> shipment coordination (escalate/approve),
  then shows HARD-hold scenarios: a mis-wired request whose own
  `:effect` is not `:propose`, an unrecognized op, maintenance
  scheduled against an UNVERIFIED/unregistered equipment unit, a
  shipment coordinated against an UNVERIFIED/unregistered batch, a
  shipment proposal that would exceed the batch's own logged
  production quantity, a proposal that tries to ACTUATE crafting/
  assembly equipment directly (permanently blocked, no override), a
  double-schedule of the same maintenance window, a production-batch
  patch with a fabricated instrument-family, a production-batch patch
  with an implausible pitch-accuracy-cents reading, a production-batch
  patch with an implausible wood-moisture-content-percent reading, and
  a production-batch patch with an implausible defect-rate reading.

  Like every sibling actor's own demo, each check is exercised directly
  and independently below, one request per HARD-hold scenario, the SAME
  'exercise the failure mode directly, never only via a happy-path
  actuation' discipline `parksafety`'s ADR-2607071922 Decision 5 and
  every sibling since establish."
  (:require [langgraph.graph :as g]
            [musicinstrmfg.store :as store]
            [musicinstrmfg.operation :as op]))

(def coordinator {:actor-id "coord-1" :actor-role :workshop-coordinator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}} {:thread-id tid :resume? true}))

(defn -main [& _args]
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)]

    (println "== log-production-batch batch-001 (clean patch -> phase-3 auto-commit) ==")
    (println (exec-op actor "t1"
                       {:op :log-production-batch :effect :propose :subject "batch-001"
                        :patch {:instrument-family :strings :last-assessed "2026-07-14"}}
                       coordinator))

    (println "== schedule-maintenance mnt-1 on woodshop-001 (verified, registered, woodworking bench -- escalates, approve) ==")
    (let [r (exec-op actor "t2"
                      {:op :schedule-maintenance :effect :propose :subject "mnt-1"
                       :value {:equipment-id "woodshop-001" :maintenance-type :blade-and-fence-inspection
                               :scheduled-date "2026-08-01" :actuate-equipment? false}}
                      coordinator)]
      (println r)
      (println "-- human workshop supervisor approves --")
      (println (approve! actor "t2")))

    (println "== flag-safety-concern concern-1 on woodshop-001 (always escalates -- approve) ==")
    (let [r (exec-op actor "t3"
                      {:op :flag-safety-concern :effect :propose :subject "concern-1"
                       :value {:equipment-id "woodshop-001" :severity :moderate
                               :description "wood-dust collector filter saturation reading elevated"}}
                      coordinator)]
      (println r)
      (println "-- human workshop supervisor approves --")
      (println (approve! actor "t3")))

    (println "== coordinate-shipment ship-1 on batch-001 (verified, registered, within quantity -- escalates, approve) ==")
    (let [r (exec-op actor "t4"
                      {:op :coordinate-shipment :effect :propose :subject "ship-1"
                       :value {:batch-id "batch-001" :units 50.0
                               :destination "buyer-retailer-north"}}
                      coordinator)]
      (println r)
      (println "-- human shipping approver approves --")
      (println (approve! actor "t4")))

    (println "\n-- HARD-hold scenarios --\n")

    (println "== log-production-batch with :effect other than :propose -> HARD hold (structural) ==")
    (println (exec-op actor "t5"
                       {:op :log-production-batch :effect :direct-write :subject "batch-001"
                        :patch {:instrument-family :strings}}
                       coordinator))

    (println "== unrecognized op -> HARD hold ==")
    (println (exec-op actor "t6"
                       {:op :actuate-woodshop-unit :effect :propose :subject "batch-001"}
                       coordinator))

    (println "== schedule-maintenance mnt-2 on finishing-002 (UNVERIFIED/unregistered finishing station -> HARD hold) ==")
    (println (exec-op actor "t7"
                       {:op :schedule-maintenance :effect :propose :subject "mnt-2"
                        :value {:equipment-id "finishing-002" :maintenance-type :spray-booth-filter-check
                                :scheduled-date "2026-08-01" :actuate-equipment? false}}
                       coordinator))

    (println "== coordinate-shipment ship-2 on batch-003 (UNVERIFIED/unregistered batch -> HARD hold) ==")
    (println (exec-op actor "t8"
                       {:op :coordinate-shipment :effect :propose :subject "ship-2"
                        :value {:batch-id "batch-003" :units 20.0
                                :destination "buyer-retailer-south"}}
                       coordinator))

    (println "== coordinate-shipment ship-3 on batch-002 (10 units would exceed quantity 60 vs shipped 55 -> HARD hold) ==")
    (println (exec-op actor "t9"
                       {:op :coordinate-shipment :effect :propose :subject "ship-3"
                        :value {:batch-id "batch-002" :units 10.0
                                :destination "buyer-retailer-east"}}
                       coordinator))

    (println "== schedule-maintenance mnt-3 on woodshop-001 with :actuate-equipment? true -> HARD hold, PERMANENT, never reaches a human ==")
    (println (exec-op actor "t10"
                       {:op :schedule-maintenance :effect :propose :subject "mnt-3"
                        :value {:equipment-id "woodshop-001" :maintenance-type :force-run
                                :scheduled-date "2026-09-01" :actuate-equipment? true}}
                       coordinator))

    (println "== schedule-maintenance mnt-1 AGAIN (double-schedule -> HARD hold) ==")
    (println (exec-op actor "t11"
                       {:op :schedule-maintenance :effect :propose :subject "mnt-1"
                        :value {:equipment-id "woodshop-001" :maintenance-type :blade-and-fence-inspection
                                :scheduled-date "2026-08-01" :actuate-equipment? false}}
                       coordinator))

    (println "== log-production-batch batch-001 with a fabricated instrument-family -> HARD hold ==")
    (println (exec-op actor "t12"
                       {:op :log-production-batch :effect :propose :subject "batch-001"
                        :patch {:instrument-family :theremin-experimental}}
                       coordinator))

    (println "== log-production-batch batch-001 with an implausible pitch-accuracy-cents -> HARD hold ==")
    (println (exec-op actor "t13"
                       {:op :log-production-batch :effect :propose :subject "batch-001"
                        :patch {:pitch-accuracy-cents 500}}
                       coordinator))

    (println "== log-production-batch batch-001 with an implausible wood-moisture-content-percent -> HARD hold ==")
    (println (exec-op actor "t14"
                       {:op :log-production-batch :effect :propose :subject "batch-001"
                        :patch {:wood-moisture-content-percent -5.0}}
                       coordinator))

    (println "== log-production-batch batch-001 with an implausible defect-rate reading -> HARD hold ==")
    (println (exec-op actor "t15"
                       {:op :log-production-batch :effect :propose :subject "batch-001"
                        :patch {:defect-rate-percent 999.0}}
                       coordinator))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== draft maintenance records ==")
    (doseq [r (store/maintenance-history db)] (println r))

    (println "\n== draft shipment records ==")
    (doseq [r (store/shipment-history db)] (println r))))
