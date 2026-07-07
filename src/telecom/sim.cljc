(ns telecom.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean line through
  intake -> identity verification -> billing-dispute screening ->
  number-provisioning proposal (always escalates) -> human approval ->
  commit, then through billing-suppression proposal (always
  escalates) -> human approval -> commit, then shows five HARD holds
  (a jurisdiction with no spec-basis, a malformed E.164 number, an
  unresolved billing dispute screened directly via `:billing/screen`
  [never via an actuation op against an unscreened line -- see this
  actor's own governor ns docstring / the lesson `parksafety`'s
  ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s and `water`'s ADR-0001s
  already recorded], and a double number-provisioning/billing-
  suppression of an already-processed line) that never reach a human
  at all, and prints the audit ledger + the draft number-provisioning
  and billing-suppression records."
  (:require [langgraph.graph :as g]
            [telecom.store :as store]
            [telecom.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :telecom-operator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== line/intake line-1 (JPN, clean; valid E.164 number, no billing dispute) ==")
    (println (exec! actor "t1" {:op :line/intake :subject "line-1"
                                :patch {:id "line-1" :holder-name "Sakura Community Network"}} operator))

    (println "== identity/verify line-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :identity/verify :subject "line-1"} operator))
    (println (approve! actor "t2"))

    (println "== billing/screen line-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :billing/screen :subject "line-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/provision-number line-1 (always escalates -- actuation/provision-number) ==")
    (let [r (exec! actor "t4" {:op :actuation/provision-number :subject "line-1"} operator)]
      (println r)
      (println "-- human telecom operator approves --")
      (println (approve! actor "t4")))

    (println "== actuation/suppress-billing-record line-1 (always escalates -- actuation/suppress-billing-record) ==")
    (let [r (exec! actor "t5" {:op :actuation/suppress-billing-record :subject "line-1"} operator)]
      (println r)
      (println "-- human telecom operator approves --")
      (println (approve! actor "t5")))

    (println "== identity/verify line-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :identity/verify :subject "line-2" :no-spec? true} operator))

    (println "== identity/verify line-3 (escalates -- human approves; sets up the malformed-number test) ==")
    (println (exec! actor "t7" {:op :identity/verify :subject "line-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/provision-number line-3 (\"0312345678\" is not valid E.164 -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/provision-number :subject "line-3"} operator))

    (println "== billing/screen line-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :billing/screen :subject "line-4"} operator))

    (println "== actuation/provision-number line-1 AGAIN (double-provisioning -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/provision-number :subject "line-1"} operator))

    (println "== actuation/suppress-billing-record line-1 AGAIN (double-suppression -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/suppress-billing-record :subject "line-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft number-provisioning records ==")
    (doseq [r (store/provisioning-history db)] (println r))

    (println "== draft billing-suppression records ==")
    (doseq [r (store/suppression-history db)] (println r))))
