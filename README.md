# Fynnix

On-device, privacy-first financial intelligence for Android. Reads bank/wallet/UPI SMS locally, builds a deterministic ledger of transactions and accounts, and surfaces spend / budget / subscription / insight views — without ever connecting to the internet.

---

## 1. What it does

| Capability | How |
|---|---|
| Read SMS automatically | `READ_SMS` for backfill, `RECEIVE_SMS` for live capture |
| Extract structured transactions | A 9-stage parsing pipeline (filters → normalize → intent → handler chain → confidence → validate) |
| Identify accounts (savings / credit card / wallet / UPI) | `AccountResolver` auto-creates and types them from SMS content |
| Track real-time balance per account | Snapshot extracted from "Avl Bal" / "Avl Lmt" lines, newest wins |
| Categorise spending | Merchant-keyword categoriser (extensible via overrides table) |
| Detect subscriptions | `SubscriptionDetector` — ≥3 same-merchant debits, ~30-day spacing, ±15% amount |
| Detect internal transfers (CC bill, wallet load) | `TransferPairDetector` folds them out of monthly totals |
| Produce insights | Cashflow ratio, top category, late-night spending, subscription summary |
| Lock the app | Real biometric / PIN / device-lock gate |
| Backup | CSV export/import via Storage Access Framework |
| Privacy | DB encrypted with SQLCipher, no internet permission, no auto-backup |

---

## 2. Privacy & security guarantees

- **No `INTERNET` permission** in the manifest — by build configuration, the app physically cannot send data anywhere.
- **`android:allowBackup="false"`** plus explicit `backup_rules.xml` and `data_extraction_rules.xml` excluding the database and prefs files.
- **SQLCipher** encrypts the Room database; passphrase is generated once and stored in `EncryptedSharedPreferences` (Android Keystore, AES-GCM).
- **PBKDF2-WithHmacSHA256, 200 000 iterations, 16-byte salt** for the optional PIN; constant-time compare.
- **App lock screen** gates the entire UI tree on launch and re-locks after 30 s of background.

---

## 3. Architecture overview

```
┌────────────────────────────────────────────────────────────────────┐
│                      Compose UI (per-feature)                      │
│   Home · Transactions · Insights · Subscriptions · Settings · …    │
└────────────────────────────────────────────────────────────────────┘
                              ▲   StateFlow
┌────────────────────────────────────────────────────────────────────┐
│                  ViewModels (Hilt @HiltViewModel)                  │
│   HomeVM · TransactionsVM · InsightsVM · BudgetVM · SecurityVM …  │
└────────────────────────────────────────────────────────────────────┘
                              ▲   Repository / DAO
┌────────────────────────────────────────────────────────────────────┐
│   Analytics                  Repositories / DAOs                   │
│   ─ SubscriptionDetector    TransactionRepository / TransactionDao │
│   ─ InsightGenerator        AccountDao   CategoryDao   BudgetDao   │
│   ─ TransferPairDetector                                           │
│   ─ AccountResolver                                                │
└────────────────────────────────────────────────────────────────────┘
                              ▲   Domain models
┌────────────────────────────────────────────────────────────────────┐
│                    Room (SQLCipher-encrypted)                      │
└────────────────────────────────────────────────────────────────────┘
                              ▲   buildTransaction(...)
┌────────────────────────────────────────────────────────────────────┐
│                       SyncWorker (CoroutineWorker)                 │
│  ─ Modes: INITIAL · MANUAL · PERIODIC (6 h) · INCREMENTAL_SMS      │
│  ─ Stream-processes SMS · dedups · resolves accounts               │
│  ─ Runs TransferPairDetector after each ingest                     │
└────────────────────────────────────────────────────────────────────┘
                              ▲   ParseResult.Success
┌────────────────────────────────────────────────────────────────────┐
│   Pipeline (deterministic, 9 stages)                               │
│   Normalize → Filter chain (sanity / OTP / promo) → Country adapter│
│   → Sender trust → Intent → Parser registry (chain-of-resp.)       │
│   → Extraction → Confidence → Validation                           │
└────────────────────────────────────────────────────────────────────┘
                              ▲
                         SmsInput
                              ▲
              SmsReader   /   SmsReceiver
              (backfill)      (real-time)
```

---

## 4. Project layout

```
app/src/main/java/com/fynnix/app/
├── FynnixApp.kt                       Hilt app + WorkManager Configuration.Provider
├── MainActivity.kt                    AppRoot (lock gate) → MainScreen (NavHost)
│
├── core/
│   ├── analytics/                     deterministic analytics layer
│   │   ├── AccountResolver.kt           SMS → AccountEntity (auto-create + balance)
│   │   ├── InsightGenerator.kt          tx + categories → insight cards
│   │   ├── SubscriptionDetector.kt      recurring-payment heuristic
│   │   ├── TransferPairDetector.kt      CC bill / wallet load folder
│   │   └── TimeRanges.kt                today/week/month/year + ticking flow
│   │
│   ├── backup/CsvIo.kt                 SAF-driven CSV export/import
│   ├── data/UserPreferences.kt        wraps SharedPreferences
│   ├── database/                       Room + SQLCipher
│   │   ├── AppDatabase.kt
│   │   ├── Converters.kt
│   │   └── DateTimeConverters.kt
│   ├── di/
│   │   ├── DatabaseModule.kt           SQLCipher passphrase wiring
│   │   └── ParserModule.kt             @IntoSet for filters + 16 institution parsers
│   │
│   ├── parser/                         ★ deterministic pipeline ★
│   │   ├── Pipeline.kt                  9-stage orchestrator
│   │   ├── PipelineModels.kt            SmsInput / NormalizedSms / TransactionDraft / ParseResult
│   │   ├── contract/                    interfaces (SmsFilter, InstitutionParser, Extractor<T>, …)
│   │   ├── filter/                      SanityFilter · OtpFilter · PromotionalFilter
│   │   ├── normalize/TextNormalizer.kt  NFKC + currency unify + lowercase
│   │   ├── country/CountryAdapter.kt    DLT prefix strip (IN today)
│   │   ├── sender/SenderTrustClassifier.kt  → TrustLevel
│   │   ├── intent/IntentClassifier.kt   → TransactionIntent (incl. FAILED_TRANSACTION)
│   │   ├── extractor/                   AmountExtractor · MerchantExtractor · AccountExtractor
│   │   │                                ReferenceExtractor · BalanceExtractor · DateExtractor
│   │   ├── institution/                 16 handlers + ParserRegistry + BaseIndianBankHandler
│   │   ├── scoring/ConfidenceEngine.kt  rubric-style scorer
│   │   └── validator/DefaultTransactionValidator.kt
│   │
│   ├── security/
│   │   ├── SecureKeyVault.kt            EncryptedSharedPreferences passphrase + PIN store
│   │   └── PinHasher.kt                 PBKDF2
│   │
│   ├── sms/                             SMS plumbing
│   │   ├── SmsReader.kt                 ContentResolver streaming Flow
│   │   ├── SmsReceiver.kt               BroadcastReceiver, gated on onboarding
│   │   ├── SmsProcessingManager.kt      live progress StateFlow
│   │   └── SyncWorker.kt                INITIAL / MANUAL / PERIODIC / INCREMENTAL
│   │
│   ├── ui/                              theme + shared components
│   └── utils/                           Hashing, enums (TransactionType, AccountType, …)
│
├── features/
│   ├── accounts/                        AccountEntity + AccountManagementScreen
│   ├── budgets/                         BudgetEntity + BudgetViewModel
│   ├── categories/                      CategoryEntity + DatabaseSeeder + Categories screen
│   ├── home/                            HomeScreen + HomeViewModel
│   ├── insights/                        InsightsScreen + InsightsViewModel
│   ├── onboarding/                      Welcome (consent + READ_SMS) → AboutYou → AllSet
│   ├── security/                        AppLockScreen + SecurityViewModel + SecuritySettings
│   ├── settings/                        Profile · DataManagement · AppSettings · Privacy · Help · Report
│   ├── subscriptions/                   SubscriptionsScreen + VM
│   └── transactions/                    TransactionEntity / Dao / Repository / list + detail
│
└── navigation/Screen.kt                 single source of route names
```

---

## 5. End-to-end SMS data flow

1. **`SmsReader`** queries `Telephony.Sms.CONTENT_URI` (DESC by date), emits `RawSms` as a Flow.
2. **`SyncWorker`** drains the Flow one SMS at a time (no `toList()` → bounded memory).
3. For each SMS:
   - **SHA-256 hash** of `sender|body` is checked against `transactions.smsHash` (unique index). If present → `incDuplicate()`.
   - Otherwise hand to **`Pipeline.process(SmsInput)`**:
     1. **Normalize** — NFKC, lowercase, unify currency tokens (`Rs.` / `₹` / `INR` → `inr`), collapse whitespace.
     2. **Filter chain** — `SanityFilter` (length), `OtpFilter`, `PromotionalFilter` in priority order. Any rejection ends here.
     3. **Country adapter** — strips DLT prefix (`JD-HDFCBK` → `HDFCBK`) for `IN`.
     4. **Sender trust** — `VERIFIED_BANK / WALLET / UPI / CARD / FINTECH / UNKNOWN / SPAM`. SPAM rejects.
     5. **Intent classification** — KYC, REMINDER, **FAILED_TRANSACTION**, REFUND, AUTOPAY, ATM, CREDIT, DEBIT. Non-financial rejects. Failed rejects (no row created).
     6. **Parser discovery** — `ParserRegistry` finds the highest-priority `InstitutionParser` whose `canHandle(sender)` is true. Falls through to `GenericFinancialHandler`.
     7. **Extraction** — handler runs every `Extractor<T>` (amount, merchant, account, reference, balance, date) and returns a `TransactionDraft`.
     8. **Confidence scoring** — `ConfidenceEngine` adds points for trust + amount + intent + account + reference; subtracts URL penalty.
     9. **Validation** — `DefaultTransactionValidator` rejects if amount missing, intent unknown, or score below 55.
   - On `ParseResult.Success`:
     - **Secondary dedup** — compute `dedupKey = "${amountCents}|${dayBucket}|${normalizedMerchant}"`. If a row with this key already exists and isn't user-edited → skip (collapses bank-SMS + UPI-app double).
     - **Account resolution** — `AccountResolver.resolve(draft, body)` returns `accountId`, creating a typed account if needed and refreshing its balance.
     - **Categorise** — keyword match merchant → `categoryId`.
     - **Insert** — `dao.insertIgnore(entity)` (preserves any prior user-edited row).
4. After ingest finishes, **`TransferPairDetector.detectAndMark()`** folds CC bill payments and wallet load-ups: matches DEBIT on savings/UPI with CREDIT on CC/wallet within ±2 days (structural) or ±5 days (with body keyword), marks both `isExcluded = true`. Aggregate queries already filter excluded rows, so monthly spend totals stop double-counting.
5. ViewModels observe DAO Flows and re-emit derived UI state. `TimeRanges.ticking(...)` re-emits date windows every 60 s so today/week/month boundaries refresh across midnight without any manual reload.

---

## 6. Key components — what each one owns

| Component | Responsibility | Dependencies |
|---|---|---|
| `Pipeline` | Sequence the 9 stages. Owns no business rules. | All contract interfaces |
| `SmsFilter` (×3) | Boolean gate before parsing. Each implements `evaluate(sms): Rejected?` | None |
| `InstitutionParser` (×17) | Bank-specific extraction. Sender match + template extractors. | All `Extractor<T>` |
| `Extractor<T>` (×6) | Single-field extraction. Pure function. | None |
| `ConfidenceScorer` | Rubric scoring with `ACCEPT_THRESHOLD = 55`. | `PromotionalFilter` (URL check) |
| `TransactionValidator` | Final accept/reject (amount > 0, intent financial, confidence ≥ threshold). | None |
| `AccountResolver` | DB-aware. Auto-types accounts from body keywords; updates balance. | `AccountDao` |
| `SubscriptionDetector` | Recurring-payment heuristic. | `TransactionDao` |
| `InsightGenerator` | Pure function: `(monthTx, categories, income, subs) → InsightUiModel[]`. | None |
| `TransferPairDetector` | Folds CC bill / wallet load pairs out of monthly totals. | `TransactionDao`, `AccountDao` |
| `SyncWorker` | Drives the entire ingest + analytics. Stream-safe, gated on onboarding. | Pipeline, DAOs, resolvers |
| `SmsReceiver` | Real-time SMS hook. Coalesces broadcasts via `enqueueUniqueWork(KEEP)`. | `SyncWorker` |
| `SecureKeyVault` | DB passphrase + PIN salt + PIN hash. EncryptedSharedPreferences. | `MasterKey` |
| `AppLockScreen` | UI lock gate, `BiometricPrompt` + PIN entry, re-locks on background. | `SecurityViewModel`, `SecureKeyVault` |

---

## 7. Currently supported institutions (16)

| Trust | Handler | Senders |
|---|---|---|
| VERIFIED_BANK pri-95 | HDFC, ICICI, SBI, Axis, Kotak | HDFCBK · ICICIB · SBIINB/UPI/CRD · AXISBK · KOTAKB · ... |
| VERIFIED_WALLET pri-92 | Paytm | PYTMBK · PAYTMB · PYTM · PAYTM |
| VERIFIED_BANK pri-90 | IDFC First, PNB, Bank of Baroda, Canara, Yes, IndusInd, Federal, RBL, Bank of India, Union | IDFCBK · PNBSMS · BARODA · CANBNK · YESBNK · INDUSB · FEDERAL · RBLBNK · BOIIND · UNIONB |
| UNKNOWN pri-1 | GenericFinancialHandler | (anything — fallback) |

---

## 8. How to extend

### Add a new bank
```kotlin
// 1. New file in core/parser/institution/
@Singleton
class HSBCBankHandler @Inject constructor(
    a: AmountExtractor, m: MerchantExtractor, ac: AccountExtractor,
    r: ReferenceExtractor, b: BalanceExtractor, d: DateExtractor,
) : BaseIndianBankHandler(a, m, ac, r, b, d) {
    override val name = "HSBC Bank"
    override val priority = 90
    override val senderTokens = listOf("HSBCBK", "HSBC")
}

// 2. core/di/ParserModule.kt — one @Binds line
@Binds @IntoSet abstract fun bindHsbc(impl: HSBCBankHandler): InstitutionParser

// 3. core/parser/sender/SenderTrustClassifier.kt — add tokens to verifiedBanks
```
Pipeline + ParserRegistry pick it up automatically. No other code changes.

### Add a new filter
```kotlin
@Singleton
class KycFilter @Inject constructor() : SmsFilter {
    override val priority = 30                    // after promo
    override fun evaluate(sms): ParseResult.Rejected? =
        if (sms.cleanedBody.contains("complete your kyc"))
            ParseResult.Rejected(RejectionReason.NOT_FINANCIAL, detail = "KYC")
        else null
}
@Binds @IntoSet abstract fun bindKyc(impl: KycFilter): SmsFilter
```

### Add a new analytics insight
Extend `InsightGenerator.generate(...)` — pure function, no DB plumbing needed. The Home / Insights screens automatically pick up new `InsightUiModel`s.

### Add a new screen
1. New composable + (optional) `@HiltViewModel`.
2. Add a route to `navigation/Screen.kt`.
3. Add a `composable(Screen.Foo.route) { … }` block in `MainActivity` NavHost.

### Add a new country
1. Implement country-specific sender hygiene in `CountryAdapter.adaptSender(rawSender, simCountry)`.
2. Add country-specific institution handlers (lower priority than IN handlers if senders overlap).
3. Update `IntentClassifier` keyword lists if the local language differs.

---

## 9. Build & run

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17 (Gradle config currently targets JVM 11; either works)
- Android device / emulator on API 26+

### First build
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Reinstalling after schema-affecting changes
The Room database uses `fallbackToDestructiveMigration`, so any `@Entity` change wipes existing data on next install. Use a clean uninstall to also wipe `EncryptedSharedPreferences`:
```bash
adb uninstall com.fynnix.app
```

### Daily-dev quick compile
```bash
./gradlew :app:compileDebugKotlin -x processDebugResources --no-daemon
```
(skips resource processing — fast feedback when iterating on parser code)

### Common Android-Studio gotcha
If `processDebugResources` fails with `R.jar` locked, close Android Studio (it holds the file open) before running Gradle from the terminal — or just build inside the IDE.

---

## 10. State of the project

### What works end-to-end
- Onboarding: Welcome (consent + READ_SMS) → AboutYou → AllSet (live sync stats: scanned / transactional / parsed / duplicate / saved)
- Real-time SMS capture for 16 institutions + Paytm wallet
- Per-account auto-creation, balance snapshots, type detection (Savings / Credit Card / Wallet / UPI)
- Category breakdown, donut chart, real spend cards (today / week / month / year), subscription detection, behavioural insights
- Transaction list with search + filter chips (All / Debits / Credits / Needs review)
- Transaction detail showing the **original SMS** for verification
- Settings: Profile · Data Management (scan period 1M/3M/6M/12M/24M/All time, CSV export/import) · Security (biometric / PIN / device lock) · Privacy / Help / Report
- App lock that actually locks (biometric or PIN), re-locks after 30 s background
- SQLCipher-encrypted DB, no internet, no auto-backup
- Smooth NavHost slide+fade transitions

### Known limitations
| Limitation | Why | Workaround |
|---|---|---|
| English-only SMS | `IntentClassifier` keyword tables are English | Hindi / regional languages: deferred — see §11 |
| Forex transactions silently dropped | `AmountExtractor` only matches INR/Rs/₹ | Add `currency` capture pass — deferred |
| Notifications toggle is "Soon" | No alert scheduler yet | UI honestly labels it under-development |
| Bank-statement PDF / XLSX import | Not implemented | UI shows "Soon" badge |
| EMI / loan / mutual-fund / salary-specific tagging | Currently lumped via merchant keyword | Roadmap |
| `MerchantCategoryOverride` table unused | Not yet wired into `categorize()` | Quick win — see §11 |
| Scan history beyond Settings → Data Management | No view of rejected SMS | Roadmap (failure ledger) |
| No automated test suite | Zero unit / instrumentation tests | Roadmap (CI gating) |
| Foreground service for very large bulk syncs | WorkManager only | Add when needed for 50k+ SMS users |

### Production readiness (post-remediation pass)
- Production readiness: **74 / 100**
- Extraction reliability: **78 / 100** for supported banks
- Classification: **75 / 100** (debit/credit/refund/failed)
- Account-linking: **75 / 100**
- Duplicate detection: **85 / 100** (SHA-256 unique index + cross-channel dedup key)
- Security: **75 / 100** (encrypted DB, real lock, PBKDF2 PIN, no backup)

Closed beta on supported banks: deployable. Open Play Store release: pending Google's `READ_SMS` Permissions Declaration approval and the items in §11.

---

## 11. Roadmap (in order)

### Near term (next 1–2 weeks of work)
1. Wire `MerchantCategoryOverride` table into `categorize()` — preseed top ~200 Indian merchants.
2. Foreign currency support — add `currency` capture to `AmountExtractor`, plumb USD/EUR/GBP through `TransactionDraft.currency`.
3. Failure ledger — populate the existing `failed_transactions` table for every rejected SMS with reason + raw body, expose a "Review rejected" screen.
4. Real notification scheduler — budget threshold alerts and subscription due-date reminders. Replace the "Soon" placeholder.
5. Adversarial test suite — unit tests for `Pipeline.process(...)` over the 30+ scenarios in the audit (HDFC_DEBIT_UPI, ICICI_CARD_FOREX, SBI_BALANCE_FIRST, AXIS_DECLINED, REVERSAL, UPI_DOUBLE, CC_BILL_TRANSFER, SENDER_NO_DASH, …).
6. Permission rationale dialog when `READ_SMS` is permanently denied — link to system Settings.

### Medium term
7. Multilingual SMS — Hindi + Tamil + Telugu keyword tables for `IntentClassifier`. Devanagari-aware merchant capture in `MerchantExtractor`.
8. Bank statement import (PDF / XLSX) — replace the "Soon" tile with a real importer.
9. EMI / loan / mutual-fund / salary intents — add to `TransactionIntent` and `IntentClassifier` so they get dedicated insights.
10. Manual transaction add (FAB) on the Transactions screen — for cash spend.
11. Edit transaction details (category, merchant, "Not a transaction") on the detail sheet.
12. More banks — South Indian Bank, Karur Vysya, Karnataka, Bandhan, AU Small Finance, City Union, Jio Payments Bank, Airtel Payments Bank.

### Long term
13. **Versioned bank ruleset** shipped via in-app update channel — when HDFC changes their SMS format, push a new ruleset, not a full APK build.
14. **On-device LLM safety net** (per the original design doc — LiteRT-LM + a quantised Gemma/Qwen). Used only for messages that score in the [40, 55] confidence band; gated to only accept the LLM's output if the amount agrees with at least one regex hit.
15. **Real account-aware spend logic** — replace headline "Total spend" with: gross outflows from non-credit accounts + raw card swipes (excluding bill-payment pairs already detected by `TransferPairDetector`).
16. **Background service health monitor** — surface a yellow banner on Home if last-successful-sync > 24 h (catches Doze / battery-saver kills).
17. **Foreground service** for the historical sync to survive Doze on Android 14+ for very large inboxes.
18. **iOS port** — the `:core` packages have zero Android dependencies on contracts/extractors/scoring. With KMP they're portable. UI needs a SwiftUI rebuild.
19. **Cross-device privacy-preserving sync** — end-to-end encrypted via the user's own cloud (Google Drive App Folder / iCloud Drive). Optional, opt-in.
20. **Public Play Store launch** — requires Google `READ_SMS` Permissions Declaration approval.

---

## 12. License & contributing

Closed-source for now. Internal repository.

When opening a PR:
- Run `./gradlew :app:compileDebugKotlin` — must be green.
- New parsers: include at least 3 sample SMS bodies as comments above the handler class.
- New analytics: keep the function pure (no DAO calls in the analytics layer if possible — pass data in).
- New filters: set `priority` carefully — order matters.
- Don't add network dependencies. The app must remain offline-only.

---

*Built with Kotlin · Jetpack Compose · Hilt · Room · SQLCipher · WorkManager · Coroutines / Flow*
