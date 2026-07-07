(ns telecom.store
  "SSoT for the telecom actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every prior `cloud-
  itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/telecom/store_contract_test.clj), which is the whole point: the
  actor, the Telecom Access Governor and the audit ledger never know
  which SSoT they run on.

  Like `water.store`'s dual report-publication/alert-suppression
  history and every other dual-actuation sibling before it, this actor
  has TWO actuation events (provisioning a number, suppressing a
  billing record) acting on the SAME entity (a line), each with its
  OWN history collection, sequence counter and dedicated double-
  actuation-guard boolean (`:number-provisioned?`/`:billing-record-
  suppressed?`, never a `:status` value) -- the same discipline every
  prior sibling governor's guards establish, informed by `cloud-
  itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which line was
  screened for an unresolved billing dispute, which number was
  provisioned, which billing record was suppressed, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a community trusting a telecom
  operator needs, and the evidence an operator needs if a provisioning
  or billing-suppression decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [telecom.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (line [s id])
  (all-lines [s])
  (billing-screen-of [s line-id] "committed billing-dispute screening verdict for a line, or nil")
  (identity-verification-of [s line-id] "committed identity verification, or nil")
  (ledger [s])
  (provisioning-history [s] "the append-only number-provisioning history (telecom.registry drafts)")
  (suppression-history [s] "the append-only billing-suppression history (telecom.registry drafts)")
  (next-provisioning-sequence [s jurisdiction] "next provisioning-number sequence for a jurisdiction")
  (next-suppression-sequence [s jurisdiction] "next suppression-number sequence for a jurisdiction")
  (line-already-provisioned? [s line-id] "has this line's number already been provisioned?")
  (line-already-suppressed? [s line-id] "has this line's billing record already been suppressed?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-lines [s lines] "replace/seed the line directory (map id->line)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained line set covering both actuation lifecycles
  (provisioning a number, suppressing a billing record) so the actor +
  tests run offline."
  []
  {:lines
   {"line-1" {:id "line-1" :holder-name "Sakura Community Network"
              :e164-number "+819012345678"
              :billing-dispute-unresolved? false
              :number-provisioned? false :billing-record-suppressed? false
              :jurisdiction "JPN" :status :intake}
    "line-2" {:id "line-2" :holder-name "Atlantis Co-op"
              :e164-number "+819012345679"
              :billing-dispute-unresolved? false
              :number-provisioned? false :billing-record-suppressed? false
              :jurisdiction "ATL" :status :intake}
    "line-3" {:id "line-3" :holder-name "鈴木ライン"
              :e164-number "0312345678"
              :billing-dispute-unresolved? false
              :number-provisioned? false :billing-record-suppressed? false
              :jurisdiction "JPN" :status :intake}
    "line-4" {:id "line-4" :holder-name "田中回線"
              :e164-number "+819012345680"
              :billing-dispute-unresolved? true
              :number-provisioned? false :billing-record-suppressed? false
              :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- provision-number!
  "Backend-agnostic `:line/mark-provisioned` -- looks up the line via
  the protocol and drafts the number-provisioning record, and returns
  {:result .. :line-patch ..} for the caller to persist."
  [s line-id]
  (let [ln (line s line-id)
        seq-n (next-provisioning-sequence s (:jurisdiction ln))
        result (registry/register-number-provisioning line-id (:jurisdiction ln) seq-n)]
    {:result result
     :line-patch {:number-provisioned? true
                 :provisioning-number (get result "provisioning_number")}}))

(defn- suppress-billing-record!
  "Backend-agnostic `:line/mark-suppressed` -- looks up the line via
  the protocol and drafts the billing-suppression record, and returns
  {:result .. :line-patch ..} for the caller to persist."
  [s line-id]
  (let [ln (line s line-id)
        seq-n (next-suppression-sequence s (:jurisdiction ln))
        result (registry/register-billing-suppression line-id (:jurisdiction ln) seq-n)]
    {:result result
     :line-patch {:billing-record-suppressed? true
                 :suppression-number (get result "suppression_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (line [_ id] (get-in @a [:lines id]))
  (all-lines [_] (sort-by :id (vals (:lines @a))))
  (billing-screen-of [_ id] (get-in @a [:billing-screens id]))
  (identity-verification-of [_ line-id] (get-in @a [:verifications line-id]))
  (ledger [_] (:ledger @a))
  (provisioning-history [_] (:provisionings @a))
  (suppression-history [_] (:suppressions @a))
  (next-provisioning-sequence [_ jurisdiction] (get-in @a [:provisioning-sequences jurisdiction] 0))
  (next-suppression-sequence [_ jurisdiction] (get-in @a [:suppression-sequences jurisdiction] 0))
  (line-already-provisioned? [_ line-id] (boolean (get-in @a [:lines line-id :number-provisioned?])))
  (line-already-suppressed? [_ line-id] (boolean (get-in @a [:lines line-id :billing-record-suppressed?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :line/upsert
      (swap! a update-in [:lines (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :billing-screen/set
      (swap! a assoc-in [:billing-screens (first path)] payload)

      :line/mark-provisioned
      (let [line-id (first path)
            {:keys [result line-patch]} (provision-number! s line-id)
            jurisdiction (:jurisdiction (line s line-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:provisioning-sequences jurisdiction] (fnil inc 0))
                       (update-in [:lines line-id] merge line-patch)
                       (update :provisionings registry/append result))))
        result)

      :line/mark-suppressed
      (let [line-id (first path)
            {:keys [result line-patch]} (suppress-billing-record! s line-id)
            jurisdiction (:jurisdiction (line s line-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:suppression-sequences jurisdiction] (fnil inc 0))
                       (update-in [:lines line-id] merge line-patch)
                       (update :suppressions registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-lines [s lines] (when (seq lines) (swap! a assoc :lines lines)) s))

(defn seed-db
  "A MemStore seeded with the demo line set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :billing-screens {} :ledger [] :provisioning-sequences {}
                           :provisionings [] :suppression-sequences {} :suppressions []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/billing-screen payloads, ledger
  facts, provisioning/suppression records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:line/id                          {:db/unique :db.unique/identity}
   :verification/line-id             {:db/unique :db.unique/identity}
   :billing-screen/line-id           {:db/unique :db.unique/identity}
   :ledger/seq                       {:db/unique :db.unique/identity}
   :provisioning/seq                 {:db/unique :db.unique/identity}
   :suppression/seq                  {:db/unique :db.unique/identity}
   :provisioning-sequence/jurisdiction {:db/unique :db.unique/identity}
   :suppression-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- line->tx [{:keys [id holder-name e164-number
                         billing-dispute-unresolved?
                         number-provisioned? billing-record-suppressed?
                         jurisdiction status provisioning-number suppression-number]}]
  (cond-> {:line/id id}
    holder-name                            (assoc :line/holder-name holder-name)
    e164-number                            (assoc :line/e164-number e164-number)
    (some? billing-dispute-unresolved?)   (assoc :line/billing-dispute-unresolved? billing-dispute-unresolved?)
    (some? number-provisioned?)           (assoc :line/number-provisioned? number-provisioned?)
    (some? billing-record-suppressed?)    (assoc :line/billing-record-suppressed? billing-record-suppressed?)
    jurisdiction                          (assoc :line/jurisdiction jurisdiction)
    status                                (assoc :line/status status)
    provisioning-number                   (assoc :line/provisioning-number provisioning-number)
    suppression-number                    (assoc :line/suppression-number suppression-number)))

(def ^:private line-pull
  [:line/id :line/holder-name :line/e164-number
   :line/billing-dispute-unresolved? :line/number-provisioned? :line/billing-record-suppressed?
   :line/jurisdiction :line/status :line/provisioning-number :line/suppression-number])

(defn- pull->line [m]
  (when (:line/id m)
    {:id (:line/id m) :holder-name (:line/holder-name m)
     :e164-number (:line/e164-number m)
     :billing-dispute-unresolved? (boolean (:line/billing-dispute-unresolved? m))
     :number-provisioned? (boolean (:line/number-provisioned? m))
     :billing-record-suppressed? (boolean (:line/billing-record-suppressed? m))
     :jurisdiction (:line/jurisdiction m) :status (:line/status m)
     :provisioning-number (:line/provisioning-number m) :suppression-number (:line/suppression-number m)}))

(defrecord DatomicStore [conn]
  Store
  (line [_ id]
    (pull->line (d/pull (d/db conn) line-pull [:line/id id])))
  (all-lines [_]
    (->> (d/q '[:find [?id ...] :where [?e :line/id ?id]] (d/db conn))
         (map #(pull->line (d/pull (d/db conn) line-pull [:line/id %])))
         (sort-by :id)))
  (billing-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?k :billing-screen/line-id ?lid] [?k :billing-screen/payload ?p]]
              (d/db conn) id)))
  (identity-verification-of [_ line-id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?a :verification/line-id ?lid] [?a :verification/payload ?p]]
              (d/db conn) line-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (provisioning-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :provisioning/seq ?s] [?e :provisioning/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (suppression-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :suppression/seq ?s] [?e :suppression/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-provisioning-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :provisioning-sequence/jurisdiction ?j] [?e :provisioning-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-suppression-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :suppression-sequence/jurisdiction ?j] [?e :suppression-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (line-already-provisioned? [s line-id]
    (boolean (:number-provisioned? (line s line-id))))
  (line-already-suppressed? [s line-id]
    (boolean (:billing-record-suppressed? (line s line-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :line/upsert
      (d/transact! conn [(line->tx value)])

      :verification/set
      (d/transact! conn [{:verification/line-id (first path) :verification/payload (enc payload)}])

      :billing-screen/set
      (d/transact! conn [{:billing-screen/line-id (first path) :billing-screen/payload (enc payload)}])

      :line/mark-provisioned
      (let [line-id (first path)
            {:keys [result line-patch]} (provision-number! s line-id)
            jurisdiction (:jurisdiction (line s line-id))
            next-n (inc (next-provisioning-sequence s jurisdiction))]
        (d/transact! conn
                     [(line->tx (assoc line-patch :id line-id))
                      {:provisioning-sequence/jurisdiction jurisdiction :provisioning-sequence/next next-n}
                      {:provisioning/seq (count (provisioning-history s)) :provisioning/record (enc (get result "record"))}])
        result)

      :line/mark-suppressed
      (let [line-id (first path)
            {:keys [result line-patch]} (suppress-billing-record! s line-id)
            jurisdiction (:jurisdiction (line s line-id))
            next-n (inc (next-suppression-sequence s jurisdiction))]
        (d/transact! conn
                     [(line->tx (assoc line-patch :id line-id))
                      {:suppression-sequence/jurisdiction jurisdiction :suppression-sequence/next next-n}
                      {:suppression/seq (count (suppression-history s)) :suppression/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-lines [s lines]
    (when (seq lines) (d/transact! conn (mapv line->tx (vals lines)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:lines ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [lines]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-lines s lines))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo line set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
