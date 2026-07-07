(ns telecom.facts
  "Per-jurisdiction telecommunications-numbering-plan regulatory catalog
  -- the G2-style spec-basis table the Telecom Access Governor checks
  every identity/verify proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's numbering-plan
  authority, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official national
  numbering-plan administrator (see `:provenance`); they are a
  STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  identity-verification-record/e164-number-assignment-record/CDR-
  integrity-attestation/billing-dispute-log evidence set submitted in
  some form; `:legal-basis` / `:owner-authority` / `:provenance` are
  the G2 citation the governor requires before any `:identity/verify`
  proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "総務省 (MIC, Ministry of Internal Affairs and Communications)"
          :legal-basis "電気通信事業法 / 電気通信番号規則"
          :national-spec "電気通信番号の指定・使用に関する規律"
          :provenance "https://www.soumu.go.jp/"
          :required-evidence ["本人確認記録 (identity-verification-record)"
                              "E.164番号割当記録 (e164-number-assignment-record)"
                              "通話記録完全性証明 (CDR-integrity-attestation)"
                              "請求紛争台帳 (billing-dispute-log)"]}
   "USA" {:name "United States"
          :owner-authority "Federal Communications Commission (FCC) / North American Numbering Plan Administrator (NANPA)"
          :legal-basis "Communications Act of 1934, 47 U.S.C. §251 (Numbering)"
          :national-spec "North American Numbering Plan (NANP) assignment and porting rules"
          :provenance "https://www.fcc.gov/numbering"
          :required-evidence ["Identity-verification record"
                              "E.164-number-assignment record"
                              "CDR-integrity attestation"
                              "Billing-dispute log"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Office of Communications (Ofcom)"
          :legal-basis "Communications Act 2003 / National Telephone Numbering Plan"
          :national-spec "UK numbering plan allocation and porting requirements"
          :provenance "https://www.ofcom.org.uk/phones-telecoms-and-internet/information-for-industry/numbering"
          :required-evidence ["Identity-verification record"
                              "E.164-number-assignment record"
                              "CDR-integrity attestation"
                              "Billing-dispute log"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesnetzagentur"
          :legal-basis "Telekommunikationsgesetz (TKG)"
          :national-spec "Nummerierungsplan / Rufnummernzuteilung"
          :provenance "https://www.bundesnetzagentur.de/DE/Fachthemen/Telekommunikation/Nummerierung/"
          :required-evidence ["Identitätsprüfungsnachweis (identity-verification-record)"
                              "E.164-Rufnummernzuteilungsnachweis (e164-number-assignment-record)"
                              "CDR-Integritätsnachweis (CDR-integrity-attestation)"
                              "Rechnungsstreitprotokoll (billing-dispute-log)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to provision a
  number or suppress a billing record on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-6190 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `telecom.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
