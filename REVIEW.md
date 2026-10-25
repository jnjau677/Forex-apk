# Code Review — Forex Chart Analyzer (`main` @ 74adb99)

**Scope:** full repository — Android/Kotlin app (~13,800 LOC), Gradle build, CI/CD, data layer, auth, security, tests, docs.
**Method:** static review of every module + CI run history on GitHub. A local build could not be run (no JDK/Android SDK in the review environment), but several build failures are provable statically and are confirmed by GitHub Actions history (see F-02).
**Verdict:** **Not releasable, and not currently buildable from a clean checkout.** The analysis/validation core is genuinely well engineered, but it sits behind a broken build, a leaked API key, unsafe local auth, and a "live" data path that is partly fabricated. Fix the P0 list before any further feature work.

---

## 🔴 P0 — Critical

### F-01. Live FCS API key committed to the repository
`test_fcs.sh:1` contains a real-looking FCS access key (`yqU1…8QMe7W`, full value in the file and in git history since the initial merge).

- **Action (owner, outside this repo): revoke/rotate that key at fcsapi.com immediately.** Deleting the file is not enough — the key remains in git history (`git log -S` finds it) and in every clone/fork.
- Replace the script with an env-var version: `curl "https://fcsapi.com/api-v3/forex/latest?symbol=EUR/USD&access_key=${FCS_API_KEY:?}"`.
- Optionally purge history (git filter-repo) after rotation; rotation is the part that actually matters.

### F-02. The project does not build from a clean checkout (CI is permanently red)
Four independent blockers, any one of which is fatal:

1. **No Gradle wrapper.** There is no `gradlew`, `gradlew.bat`, or `gradle/wrapper/*` in the repo, yet `README.md`, `DEPLOYMENT.md`, and all three workflows invoke `./gradlew`. Verified against GitHub: every *Android CI / CD* and *Java CI with Gradle* run on `main` has failed; the latest (run `33051253661`, 2026‑08‑27) fails at "Run Unit Tests" with **exit code 127** (`./gradlew: No such file or directory`). Fix: run `gradle wrapper --gradle-version <x>` once and commit the wrapper.
2. **Missing dependency.** `SecureApiKeyManager.kt:4-6` imports `androidx.security.crypto.EncryptedSharedPreferences` / `MasterKey`, but `security-crypto` is declared nowhere in `gradle/libs.versions.toml` or `app/build.gradle.kts`. `compileDebugKotlin` cannot succeed.
3. **Signing configs point at files that don't exist.** `app/build.gradle.kts:34-40` overrides the debug signing config to `${rootDir}/debug.keystore` (git-ignored, absent in a fresh clone — so even `assembleDebug` fails at signing), and `:23-33` unconditionally applies the `release` signing config whose passwords come from unset env vars and whose default keystore `my-upload-key.jks` is absent.
4. **A test file that cannot compile** (see F-03) breaks `testDebugUnitTest` even after 1–3 are fixed.

### F-03. Stale tests are broken; test suite gives false confidence
- `app/src/test/java/com/example/ExampleUnitTest.kt` references a deleted `PriceGenerator`, constructs `CurrencyPair(basePrice = …)` (no such parameter), and asserts on `signal.confidence` / `signal.takeProfit` (actual API: `confidenceScore`, `takeProfit1/2`). This file **does not compile**, so the whole unit-test task fails.
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt:20` asserts the package is `com.example`; `applicationId` is `com.devcode.forex` (`app/build.gradle.kts:17`). It can never pass.
- The four forex-specific test classes (`TechnicalAnalysisEngineTest`, `MarketDataValidatorTest`, `RealMarketDataProviderParsingTest`, `DynamicAiResponseEngineTest`) are well written and meaningful — but they currently cannot run because of the two items above. Delete or rewrite the template tests.

### F-04. Local authentication is unsafe
`ForexAuthManager.kt`:

- **Unsalted SHA-256 password hashes stored in a plaintext Room DB** (`:68-71`, `:120`, `:171`). SHA-256 is a fast hash — trivially brute-forced if the DB leaks (and see F-09: the DB is backup-eligible). Passwords should never be stored locally at all when Firebase Auth is the backend; Firebase already persists its own session.
- **Authentication bypass:** `:193` — `if (localUser.passwordHash == inputHash || localUser.passwordHash == null)` — any account row with a null hash (e.g. created via the Google path, `:255-263`, which never sets a hash) can be "signed into" with **any** password via the email form if the email matches.
- **Phantom accounts:** on sign-up/sign-in, almost any Firebase failure is swallowed (`:98-105`, `:158-161`) and a local account is created/accepted anyway, so users get "successful" logins against a dummy backend.
- **Dummy Firebase initialisation:** `:37-48` initialises Firebase with `dummy_api_key_for_firebase` / `dummy-project` when `google-services.json` is absent (it is absent — and only `WARN`ed, `app/build.gradle.kts:75`). Fail closed with a clear "not configured" state instead.
- Dead duplicate: `GoogleAuthClient.kt` re-implements Google sign-in; `ForexViewModel.kt:25` instantiates it and never calls it. Remove one of the two paths.

### F-05. API-key "security" is decorative; keys ship inside the APK
- The Secrets Gradle plugin bakes `FCS_API_KEY`, `OPENROUTER_API_KEY`, and `ALPHAVANTAGE_API_KEY` from `.env` into `BuildConfig`. Anything in `BuildConfig` is extractable from a distributed APK with `apktool` in minutes — encryption at rest on the device does not change that.
- Worse, `SecureApiKeyManager` is **dead code**: it is never instantiated anywhere (`grep` finds only its own file). All three live consumers read `BuildConfig` directly — `RealMarketDataProvider.kt:108`, `ForexMarketRepositoryService.kt:19`, `ForexRepository.kt:370` — bypassing the encrypted store entirely. Its fallback even copies the BuildConfig key *into* encrypted prefs (`SecureApiKeyManager.kt:42-48`), which adds nothing.
- `ForexMarketRepositoryService.kt:26-28` attaches an OkHttp `HttpLoggingInterceptor` at `BASIC` level, which logs the full request line — including `?access_key=…` — to logcat. (`RealMarketDataProvider.kt:124` explicitly avoids this; FCS should too.)
- **Real fix:** for a distributed app, third-party keys must sit behind your own backend proxy, or be user-supplied keys stored via `SecureApiKeyManager` (and then actually used). At minimum, wire the existing manager in and drop the logging interceptor.

### F-06. The default "live" market data is partly fabricated — and drives signals/notifications
This is the most consequential product-integrity issue:

- `ForexWebSocketManager.kt:265-306` ("Live Tick Pulse Engine") generates **random-walk ticks for all 9 pairs every 350–750 ms regardless of socket state**. `disconnect()` (`:340-349`) closes the socket but never cancels `pulseJob`, so prices keep "ticking" after disconnect, and `dispatchTick` forces status `CONNECTED` (`:237-241`) — the UI's "Live WS Connected" state can be displayed while no connection exists.
- Real socket data is a Binance **crypto** stream (`wss://stream.binance.com:9443/ws/!miniTicker@arr`, `:71`): EUR/USD is derived from EUR/USDT (a stablecoin pair), gold from PAXG. That is not an FX spot feed and should not be presented as interbank pricing. The `!miniTicker@arr` stream also pushes ~1,000 symbols/sec through `org.json` parsing on the OkHttp thread for the sake of 5 symbols — heavy battery/bandwidth cost; subscribe to only the needed streams.
- On first launch `ForexRepository.kt:46-65` generates random historical candles and **persists them into Room**. `SignalWorker.loadCandles()` then falls back to that same Room history with no provenance check (`CandleEntity` has no source field) — directly contradicting the worker's own contract ("a background notification must only ever reflect real market data", `SignalWorker.kt:44-49`). Background BUY/SELL notifications with lot-size advice can therefore be computed from simulated data.
- Fix direction: tag every candle/tick with its source end-to-end (the `MarketDataSource` enum already exists), have the worker refuse non-live sources, label simulation unambiguously in the UI (including the connection status), and stop the pulse engine on disconnect / when a real feed is healthy.

### F-07. Price alerts can never fire
Alerts can be created, toggled, and deleted, but **nothing ever evaluates them**: no code compares price/RSI/EMA conditions against alerts, sets `isTriggered`, or notifies (grep for `updateAlertStatus` finds only the toggle path, which always writes `triggered = false`, `ForexRepository.kt:511`). README's "Local reactive trigger monitoring" is not implemented. Either implement evaluation in the tick collector (and in `SignalWorker` for the background case) or remove the feature from the README/UI promises.

---

## 🟠 P1 — High

### F-08. CI/CD is incoherent
- Three overlapping workflows (`android_ci.yml`, `gradle.yml`, `codeql.yml`) all run on every push/PR; `gradle.yml`'s `./gradlew build` and the dependency-submission job duplicate what `android_ci.yml` should own. Keep one Android workflow + CodeQL.
- **`.github/dependabot.yml` is not a Dependabot config** — it contains a copy-pasted CodeQL *workflow* YAML. There is no `version: 2` / `updates:` block, so Dependabot version updates are not configured at all. (The "Dependabot Updates" runs visible in Actions are dependency-graph alerts, and they intermittently fail.)
- Release job bugs (`android_ci.yml`): the "Decode Release Keystore" step is gated on `env.RELEASE_KEYSTORE_BASE64`, which is only defined in that step's own `env:` — the condition never sees it, so the keystore is never decoded and the release build then fails on signing. The `.env` written in CI contains only `GEMINI_API_KEY`, so release APKs would ship with placeholder FCS/OpenRouter/Alpha Vantage keys (simulation mode) even if signing worked.
- `versionCode = 1` / `versionName = "1.0"` are hard-coded with no bump mechanism — Play Console uploads would be rejected on the second release.

### F-09. Database & backup posture
- `ForexDatabase.kt:26` uses `fallbackToDestructiveMigration(dropAllTables = true)` with `exportSchema = false` at version 4. Any future schema change silently wipes watchlists, saved signals, alerts, settings, and user profiles. Add real migrations + schema export before shipping.
- `AndroidManifest.xml`: `android:allowBackup="true"` while `res/xml/backup_rules.xml` and `data_extraction_rules.xml` are empty templates. By default the Room DB — including `user_profiles.passwordHash` — is eligible for cloud backup and device transfer. Exclude the database (and any credential store) or disable backup.

### F-10. Thread-safety and performance in the hot path
- `ForexRepository.candleMap` is a plain `HashMap<String, MutableList<CandleStick>>` mutated from IO (`init`, `applyMarketTick`, network refresh) and read from Compose/main (`getCandles` is called inside `remember` in `ChartTerminalScreen.kt:65`) with no synchronisation — a real race/`ConcurrentModificationException` risk. `ForexWebSocketManager.latestPrices` has the same problem across OkHttp + coroutine threads.
- `ForexViewModel.generateAllSignals()` runs on **every** `pairs` emission — i.e. every pulse tick (350–750 ms) — recomputing EMA/RSI/MACD/ADX/Bollinger/pattern detection for 9 pairs from scratch. This will churn CPU/battery and starve the UI thread's collectors. Throttle (e.g. recompute on candle close or at most every few seconds, and only for visible/watchlisted pairs).
- `getCandles()` has side effects (DB writes + a network refresh launch) and is called during composition; first access per symbol/timeframe can trigger fetches for up to 54 combinations — against Alpha Vantage's 25-requests/day free quota. Gate network refresh explicitly (user action / worker), not on read.
- Quota accounting is per-instance and in-memory (`RealMarketDataProvider.kt:137-142`): the repository, the ViewModel path, and `SignalWorker` each construct their own provider, and process death resets the counter — so the "25/day self-throttle" does not hold in aggregate. Persist the counter (e.g. in Room/DataStore) and share one provider instance. Relatedly, a 15-minute periodic worker scanning up to 9 symbols is fundamentally incompatible with that quota; reduce symbol count/cadence or require FCS/premium for the worker.

### F-11. Release hygiene
- `isMinifyEnabled = false` (`app/build.gradle.kts:45`) and `proguard-rules.pro` is the untouched template — no shrinking/obfuscation for a finance app shipping API-key-bearing `BuildConfig` fields.
- Unused weight: `firebase-ai`, `navigation-compose` (navigation is a manual `when` over tabs in `MainActivity`), Firebase BOM, credentials stack, plus a catalog full of commented-out dependencies. `metadata.json` still claims a server-side Gemini capability that no code uses. Remove what's unused; it shrinks the APK and the attack surface.

---

## 🟡 P2 — Medium / correctness nits

- **F-12. Gold pip size disagrees with itself:** `ForexRepository.kt:164` seeds XAU/USD with `pipSize = 0.01`; `PairCatalog.kt:29` says `0.1`. ATR-in-pips, stop distances, and lot sizing differ 10× depending on which path built the pair. Single-source pair metadata in `PairCatalog`.
- **F-13. RSI series is off by one:** `TechnicalAnalysisEngine.calculateRSI` returns `closes.size + 1` entries (`period + 1` leading nulls, then a value at index `period + 1`). Index-aligned consumers (RSI subchart in `CandlestickChartCanvas.kt:424`) are shifted by one bar. Add a size assertion test.
- **F-14. Light theme is largely fictional:** ~297 hard-coded `Color(0xFF…)` literals across the UI (e.g. `RiskCalculatorScreen` is hard-coded dark slate). Toggling theme changes only the scaffold chrome. Migrate screens to `MaterialTheme.colorScheme`.
- **F-15. Risk calculator ignores the instrument:** it hard-codes "$10/pip per lot" and fixed 1.5R/3R rewards, with no pair selection — wrong for JPY pairs, gold (see F-12), and BTC. `TechnicalAnalysisEngine.contractSizeFor/computeVolatilitySizing` already do this properly; reuse them.
- **F-16. Docs don't match the code:** README's feature table names `google/gemini-2.5-flash` in one row and `mistralai/mistral-large` in another (code sends `mistral-large`); `DEPLOYMENT.md` calls compileSdk 36 an "Android 15/16 preview" and documents a release flow that cannot succeed today (F-02/F-08). Update after the P0/P1 fixes land.

## ⚪ P3 — Low / structural

- `CandlestickChartCanvas.kt` is a 2,261-line god-composable (rendering, gestures, MediaStore I/O, dialogs, snapshot state). Split drawing layers and side-effects out; it is currently untestable and slow to recompile.
- Duplicated pair definitions (`ForexRepository.createInitialPairs` vs `PairCatalog.KNOWN`, with hard-coded "current" prices that go stale instantly).
- `e.printStackTrace()` in production paths (`ForexRepository`, `ForexWebSocketManager`, `SignalWorker`) instead of structured logging/crash reporting.
- OkHttp clients and `wsScope`/`repoScope` are never shut down; `ForexWebSocketManager` starts connecting in `init` with no lifecycle owner.
- `google-services.json` absence is downgraded to a warning, deferring a config error to a runtime dummy-Firebase path (see F-04). Fail the build (or provide an explicit no-Firebase flavor) instead.

---

## ✅ What's genuinely good (keep and build on)

- **`MarketDataValidator`** — OHLC invariants, spike/gap/statistical filters, quality scoring, applied to REST, WebSocket, *and* simulated data. This is the right architecture.
- **`RealMarketDataProvider`** — CSV-over-JSON rationale, typed error taxonomy, mutex-guarded pacing, correct refusal to log the API key, and thorough JVM parsing tests.
- **`TechnicalAnalysisEngine`** — Wilder RSI/ATR, ATR-regime position sizing replacing fixed-pip stops, and unit tests that assert financial invariants (stop < entry < TP ordering, R:R = TP1/SL, lot math).
- **`SignalWorker`** — confidence/quality floors, de-duplication cooldown, grouped notifications, network-constraint + backoff. (Its data-provenance hole is F-06, not a design flaw in the worker itself.)
- Secrets hygiene basics: `.env`/keystores are git-ignored, `.env.example` uses placeholders (F-01's script being the exception).

## Recommended fix order

1. **Rotate the FCS key** (F-01) — today, before anything else.
2. Commit the Gradle wrapper; add `security-crypto`; fix/remove the two broken template tests; make `assembleDebug` + `testDebugUnitTest` green locally and in CI (F-02, F-03).
3. Decide the auth story: real Firebase (commit to `google-services.json` in CI secrets) **or** local-only — and delete the other path, the null-hash bypass, and local password storage (F-04).
4. Decide the key story: user-supplied keys via `SecureApiKeyManager` (wired in) or a backend proxy; remove `BuildConfig` keys + FCS logging interceptor (F-05).
5. Make "live" honest: source-tagged candles, worker refuses simulated data, pulse engine obeys disconnect and never claims CONNECTED (F-06); implement or de-scope alerts (F-07).
6. Then CI consolidation, Dependabot config, migrations/backup rules, quota persistence (F-08 – F-10), and release hardening (F-11).

---

## Resolution status (2026-10-20)

CI is green end-to-end for the first time: unit tests (incl. Robolectric/Roborazzi),
`assembleDebug`, and full `gradle build` (release + R8 minify, lint) all pass.
Getting there required fixing the build before any P0/P1 item could be verified:

**Why the build had never passed (all pre-existing):**
1. AGP 9.1.1 requires Gradle ≥ 9.3.1; the wrapper pinned Gradle 9.1.0, so *every*
   Gradle invocation failed in under a minute ("Minimum supported Gradle version
   is 9.3.1"). Wrapper is now 9.3.1.
2. AGP 9 built-in Kotlin rejects KSP's `kotlin.sourceSets` generated-source
   registration → `android.disallowKotlinSourceSets=false`.
3. `BuildConfig.FCS_API_KEY` / `OPENROUTER_API_KEY` / `ALPHAVANTAGE_API_KEY` were
   never declared — they existed only via the secrets-gradle-plugin (not viable
   on Gradle 9). Now declared in `defaultConfig` with env → `.env` → placeholder
   resolution. Secrets plugin removed.
4. Genuine source errors in the original tree: missing `kotlin.math.min` import,
   `EncryptedSharedPreferences` delegate typed as its impl class, `min(o, c)` on
   doubles. Fixed.
5. `MarketDataValidation` / `DynamicAiResponseEngine` behaviour bugs surfaced by
   the (previously never-run) tests: Alpha Vantage daily-CSV date strings in the
   `timestamp` column now parse; the intent router no longer matches "lot" inside
   "plot". Robolectric tests pinned to the SDK-34 sandbox (SDK-36 sandbox needs
   Java 21; CI runs JDK 17).

**P0/P1 items:** F-01 script key removed (rotation at fcsapi.com still required —
the key lives in git history); F-02 wrapper + keystore-free clean builds +
env-driven versionCode; F-03 tests rewritten/fixed and all passing; F-04 Firebase
Auth only (done previously); F-05 SecureApiKeyManager is the single key read path
(user keys win; `YOUR_*` placeholders never count as configured), FCS request
logging removed; F-06 every persisted candle tagged with its source, SignalWorker
refuses SIMULATION rows and labels signals honestly, socket uses targeted
Binance streams and the feed pill reads "Simulated Feed" unless the socket is
live; F-07 alerts evaluated on every validated tick (price/RSI/MA-cross),
persisted as triggered and notified; F-09 real 4→5 Room migration (drops only
`user_profiles`) + backup rules naming the actual DB; F-10 ConcurrentHashMap
candle cache with snapshot reads, signal regen throttled to 1/5s, Alpha Vantage
quota process-shared and persisted; F-11 R8 + resource shrinking on with real
ProGuard rules, unused firebase-ai/navigation-compose/appcheck removed.

**Still owner-side:** rotate the F-01 FCS key; create the Firebase project bits
(`app/google-services.json` or CI secret `GOOGLE_SERVICES_JSON` + register
SHA-1/SHA-256) or sign-in stays fail-closed by design; publishing a *real*
release needs `KEYSTORE_PATH`/`STORE_PASSWORD` (otherwise release falls back to
the debug key and is not uploadable); the `google-services.json` decode step for
`.github/workflows/android_ci.yml` exists locally but cannot be pushed from this
environment (GitHub App lacks `workflows` permission) — apply it from the
prepared branch state or grant the permission.
