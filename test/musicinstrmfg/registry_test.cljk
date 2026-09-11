(ns musicinstrmfg.registry-test
  (:require [clojure.test :refer [deftest is]]
            [musicinstrmfg.registry :as r]))

;; ----------------------------- equipment-verified? / equipment-registered? / equipment-ready? -----------------------------

(deftest equipment-is-verified-when-flagged
  (is (true? (r/equipment-verified? {:id "e1" :verified? true}))))

(deftest equipment-is-not-verified-when-false-or-missing
  (is (false? (r/equipment-verified? {:id "e1" :verified? false})))
  (is (false? (r/equipment-verified? {:id "e1"}))))

(deftest equipment-is-registered-when-flagged
  (is (true? (r/equipment-registered? {:registered? true}))))

(deftest equipment-is-not-registered-when-false-or-missing
  (is (false? (r/equipment-registered? {:registered? false})))
  (is (false? (r/equipment-registered? {}))))

(deftest equipment-ready-requires-both
  (is (true? (r/equipment-ready? {:verified? true :registered? true})))
  (is (false? (r/equipment-ready? {:verified? true :registered? false})))
  (is (false? (r/equipment-ready? {:verified? false :registered? true})))
  (is (false? (r/equipment-ready? {}))))

;; ----------------------------- batch-verified? / batch-registered? / batch-ready? -----------------------------

(deftest batch-is-verified-when-flagged
  (is (true? (r/batch-verified? {:id "b1" :verified? true}))))

(deftest batch-is-not-verified-when-false-or-missing
  (is (false? (r/batch-verified? {:id "b1" :verified? false})))
  (is (false? (r/batch-verified? {:id "b1"}))))

(deftest batch-is-registered-when-flagged
  (is (true? (r/batch-registered? {:registered? true}))))

(deftest batch-is-not-registered-when-false-or-missing
  (is (false? (r/batch-registered? {:registered? false})))
  (is (false? (r/batch-registered? {}))))

(deftest batch-ready-requires-both
  (is (true? (r/batch-ready? {:verified? true :registered? true})))
  (is (false? (r/batch-ready? {:verified? true :registered? false})))
  (is (false? (r/batch-ready? {:verified? false :registered? true})))
  (is (false? (r/batch-ready? {}))))

;; ----------------------------- shipment-quantity-exceeded? -----------------------------

(deftest small-shipment-within-quantity-does-not-exceed
  (is (false? (r/shipment-quantity-exceeded?
               {:quantity-units 200.0 :shipped-units 40.0} 50.0))))

(deftest shipment-that-pushes-past-quantity-exceeds
  (is (true? (r/shipment-quantity-exceeded?
              {:quantity-units 60.0 :shipped-units 55.0} 10.0))))

(deftest shipment-exactly-at-quantity-does-not-exceed
  (is (false? (r/shipment-quantity-exceeded?
               {:quantity-units 60.0 :shipped-units 55.0} 5.0))
      "exactly at quantity is not over, only strictly beyond"))

(deftest missing-quantity-is-not-flagged-exceeded
  (is (false? (r/shipment-quantity-exceeded? {} 100.0)))
  (is (false? (r/shipment-quantity-exceeded? {:quantity-units 60.0} nil))))

;; ----------------------------- instrument-family-valid? -----------------------------

(deftest known-instrument-families-are-valid
  (doseq [f [:strings :wind :percussion :keyboard]]
    (is (r/instrument-family-valid? f))))

(deftest fabricated-instrument-family-is-invalid
  (is (not (r/instrument-family-valid? :theremin-experimental)))
  (is (not (r/instrument-family-valid? nil))))

;; ----------------------------- pitch-accuracy-cents-valid? -----------------------------

(deftest typical-pitch-accuracy-cents-is-valid
  (is (r/pitch-accuracy-cents-valid? -50))
  (is (r/pitch-accuracy-cents-valid? 0))
  (is (r/pitch-accuracy-cents-valid? 5))
  (is (r/pitch-accuracy-cents-valid? 50)))

(deftest below-floor-pitch-accuracy-cents-is-invalid
  (is (not (r/pitch-accuracy-cents-valid? -51)))
  (is (not (r/pitch-accuracy-cents-valid? -500))))

(deftest excessive-pitch-accuracy-cents-is-invalid
  (is (not (r/pitch-accuracy-cents-valid? 51)))
  (is (not (r/pitch-accuracy-cents-valid? 500))))

(deftest non-integer-or-missing-pitch-accuracy-cents-is-invalid
  (is (not (r/pitch-accuracy-cents-valid? nil)))
  (is (not (r/pitch-accuracy-cents-valid? 5.5)))
  (is (not (r/pitch-accuracy-cents-valid? "5"))))

;; ----------------------------- wood-moisture-content-percent-valid? -----------------------------

(deftest typical-wood-moisture-content-percent-is-valid
  (is (r/wood-moisture-content-percent-valid? 0.1))
  (is (r/wood-moisture-content-percent-valid? 8.0))
  (is (r/wood-moisture-content-percent-valid? 30.0)))

(deftest zero-or-negative-wood-moisture-content-percent-is-invalid
  (is (not (r/wood-moisture-content-percent-valid? 0.0)))
  (is (not (r/wood-moisture-content-percent-valid? -5.0))))

(deftest excessive-wood-moisture-content-percent-is-invalid
  (is (not (r/wood-moisture-content-percent-valid? 30.01)))
  (is (not (r/wood-moisture-content-percent-valid? 100.0))))

(deftest non-numeric-or-missing-wood-moisture-content-percent-is-invalid
  (is (not (r/wood-moisture-content-percent-valid? nil)))
  (is (not (r/wood-moisture-content-percent-valid? "8.0"))))

;; ----------------------------- defect-rate-valid? -----------------------------

(deftest typical-defect-rate-is-valid
  (is (r/defect-rate-valid? 1.5))
  (is (r/defect-rate-valid? 0.0))
  (is (r/defect-rate-valid? 50.0))
  (is (r/defect-rate-valid? 100.0)))

(deftest negative-defect-rate-is-invalid
  (is (not (r/defect-rate-valid? -1.0))))

(deftest excessive-defect-rate-is-invalid
  (is (not (r/defect-rate-valid? 999.0)))
  (is (not (r/defect-rate-valid? 100.01))))

(deftest non-numeric-or-missing-defect-rate-is-invalid
  (is (not (r/defect-rate-valid? nil)))
  (is (not (r/defect-rate-valid? "1.5"))))

;; ----------------------------- register-maintenance -----------------------------

(deftest maintenance-is-a-draft-not-a-real-actuation
  (let [result (r/register-maintenance "mnt-1" "woodshop-001" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest maintenance-assigns-maintenance-number
  (let [result (r/register-maintenance "mnt-1" "woodshop-001" 7)]
    (is (= (get result "maintenance_number") "MNT-000007"))
    (is (= (get-in result ["record" "maintenance_id"]) "mnt-1"))
    (is (= (get-in result ["record" "equipment_id"]) "woodshop-001"))
    (is (= (get-in result ["record" "kind"]) "maintenance-schedule-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest maintenance-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "" "woodshop-001" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "woodshop-001" -1))))

;; ----------------------------- register-shipment -----------------------------

(deftest shipment-is-a-draft-not-a-real-dispatch
  (let [result (r/register-shipment "ship-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-shipment "ship-1" 7)]
    (is (= (get result "shipment_number") "SHP-000007"))
    (is (= (get-in result ["record" "shipment_id"]) "ship-1"))
    (is (= (get-in result ["record" "kind"]) "shipment-coordination-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "ship-1" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-maintenance "mnt-1" "woodshop-001" 0)
        hist (r/append [] c1)
        c2 (r/register-maintenance "mnt-2" "woodshop-001" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "MNT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "MNT-000001" (get-in hist2 [1 "record_id"])))))
