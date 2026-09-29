# TxnSense

An Android expense tracker that records transactions from incoming SMS and lets you add or correct them yourself.

[Download alpha APKs](https://github.com/hk121902-stack/finance-ministry/releases) · [F-Droid Repo](#install-via-f-droid) · [Roadmap](docs/ROADMAP.md) · [Privacy](docs/PRIVACY.md) · [Report a bug](https://github.com/hk121902-stack/finance-ministry/issues/new/choose)

**Status: early alpha.** The release channel is for installable **debug APKs** for testing. The app is being developed by one maintainer and is not ready for general use. Parsing can miss or misclassify transactions; check your records. Do not make this your only financial record.

The current debug alpha is [v0.1.0-alpha.10](https://github.com/hk121902-stack/finance-ministry/releases/tag/v0.1.0-alpha.10).
Download the debug APK from its assets. The release includes its SHA-256 checksum,
signing-certificate details, and license notices. Physical-device testing is pending.

## Install via F-Droid

You can install TxnSense through F-Droid using a self-hosted repository. This is useful if Play Protect blocks direct APK installation on your device.

### Add the repository

**One-click add (from your Android device with F-Droid installed):**

[![Add to F-Droid](https://img.shields.io/badge/Add_to_F_Droid-TxnSense_Alpha-blue?style=for-the-badge&logo=android)](fdroidrepo://commitandquit.github.io/finance-ministry/fdroid/repo)

Or manually:

1. Install the [F-Droid app](https://f-droid.org/) on your device
2. Open F-Droid and go to **Settings → Repositories**
3. Tap the **+** button to add a new repository
4. Enter the repository address:
   ```
   https://commitandquit.github.io/finance-ministry/fdroid/repo
   ```
5. F-Droid will show the signing key fingerprint. Verify and accept it
6. The repository will appear as "TxnSense Alpha"

### Install the app

1. In F-Droid, search for "TxnSense"
2. Tap **Install** to download and install
3. Updates will appear in F-Droid when new releases are published

**Note:** This is an unofficial self-hosted repository for testing purposes. The app is signed with the same key as the GitHub Releases APKs.

## What works today

- Capture new financial SMS after you explicitly enable SMS access.
- Save a normalized transaction first, then send an optional native notification with **View** and **Edit**.
- Keep uncertain detections in **Review**, including transactions whose amount could not be safely extracted.
- Add manual transactions with money-in/out choices, date/time pickers and optional details.
- Use separate Home and Settings screens, light/dark themes and unsaved-edit warnings.
- Edit records, keep correction history, delete individual records, or erase all local data.
- Browse history in 100-record pages, filter manual/review/edited entries, and see daily/monthly debit and credit totals.
- Extract supported masked account hints and recipient labels; conservatively link full-amount refunds/reversals when strong matching evidence exists.
- Store the ledger in an encrypted Room/SQLCipher database with keys protected by Android Keystore.

The app has no account, cloud sync, ads, analytics, payment initiation, bank API connection, or Internet permission. It supports new incoming SMS and optional history import without becoming the default SMS app.

### Import existing SMS

**Settings → Import last 3 months** offers an optional, on-device SMS history scan.
Read the disclosure and grant inbox access, inspect the preview, then confirm import.
Original SMS receipt dates are kept; repeat SMS fingerprints are skipped and uncertain
records go to Review. Import sends no per-record notifications. You can cancel or undo
the latest import; undo preserves edited and pre-existing records. Permission denial
does not block manual entry. Android/installer restrictions can prevent inbox access.
Only SMS still in the inbox are available, not deleted messages, RCS or other apps.
Each preview is limited to 20,000 SMS / 5,000 candidate transactions; exceeding either
stops the scan without importing. This is not bank reconciliation: separate alerts or
manually entered copies of the same payment may still need review.

## Try the alpha

Requires **Android 8.0 (API 26) or newer**. Current runtime verification is on a Pixel 9a Android 16 emulator; physical-device and OEM testing is still pending.

1. Open [Releases](https://github.com/hk121902-stack/finance-ministry/releases) and choose the newest **pre-release**. Download its `txnsense-*-debug.apk`, not the source-code ZIP.
2. Install the APK on a test device. Android may ask you to allow installations from the browser or Files app you used to download it. This is a one-time installer permission for that app; download only from this repository's Releases page. Do not disable Play Protect globally.
3. Open TxnSense. **+ Add transaction** works immediately without SMS or notification permissions.
4. Open **Settings → Enable SMS capture**, read the disclosure, and continue through Android's SMS permission prompt. **Pause SMS capture** means capture is currently enabled.
5. In **Settings**, enable **Recording notifications** and allow the separate Android prompt. If blocked, use **Allow Android notifications** or **Open notification settings**. The in-app switch and Android permission are independent; both must be on for notifications.
6. New eligible messages are recorded automatically. Open a row or its notification to review or correct it.

Android treats APK installation, SMS access and notification access as separate choices.
SMS capture and notifications are optional: declining either one never blocks manual
transactions. The app explains why it needs SMS access before Android shows its
permission prompt.

Manual example: enter `250.50`, keep **Money out**, and save. Cash, Other and Successful are the defaults; transaction type and payment status are under **More details**. The ledger shows **Confirmed by you**.

Emulator-only automatic example (this command does not send a real SMS):
```sh
adb -s emulator-5554 emu sms send 5551234 "INR 314.15 debited from your account via UPI"
```
With capture enabled, this produces a **Money out · Saved automatically** transaction. With notifications allowed, it also produces a notification. A message such as `Your account debited by 250` goes to **Review** because the currency amount is uncertain.

Parser version 6 recognizes the supported ICICI account-debit/recipient-credit
template and additional bank/card formats, including selected Kotak and BOBCARD layouts.
Coverage is not guaranteed. Updating does
not change existing records; use the optional import for missed SMS still in your inbox.
See the [remaining parser work](docs/ROADMAP.md#confirmed-open-parser-issues).

### Updating

Download the next APK from the same repository and install it over the existing app. Official alpha releases use one persistent signing identity and an increasing Android version code. Local developer builds use a different key and may not update an official APK in place.

**Do not uninstall to troubleshoot an update without understanding the consequence:** uninstalling or using Erase all removes the local ledger. There is currently no export, backup, cloud restore, or data recovery. Release notes will call out known upgrade restrictions. Moving to a future production signing identity may require a separate migration plan.

### Debug-build limits

These APKs are deliberately debuggable. An authorized debugging connection can inspect app state; encryption does not make a debug build equivalent to a hardened production build. Use a test device and sample transactions while evaluating it.

The app does not keep raw SMS bodies or senders in its database. Manually entered notes and labels are stored locally and can contain whatever you type; avoid pasting sensitive messages or identifiers. See [Privacy](docs/PRIVACY.md).

## How SMS parsing works

Parsing is a deterministic, on-device **template engine** (`TemplateEngineParser`). No network,
no model inference, no heuristics. Every message becomes a `ParseAssessment` with one of three
decisions — **Record** (auto-saved), **NeedsReview** (saved but flagged), or **Reject** (dropped).

**Pipeline** (in order):

1. **Strip the security footer** — a trailing fraud-reporting block ("Not you? …") carries its
   own codes and amounts, so templates never see it (`ParserRules`).
2. **Match templates** — the ~90 layouts in `TemplateRepository` are tried in order and the
   **first match wins and returns immediately**. Specific bank/card/wallet layouts come first,
   generic movements last. Every pattern is compiled once, at class load.
3. **Derive bank and card type** — the sender id (falling back to the body) sets the bank via
   `BankRegistry`; a credit-card-only issuer promotes a generic `Card` to `CreditCard`.

Each template fixes the direction, status, channel and transaction type, and pulls named groups
(`amount`, `merchant`, `account`). A message no template recognizes is **rejected**
(`no_template_match`) — coverage and accuracy grow only by adding templates, never by guessing.
A recognized layout whose amount cannot be read safely (too many decimals, overflow) is kept as
NeedsReview. Two templates flag non-INR spends for review instead of booking them as rupees.

Because matching is anchored and first-match-wins, a prefix that changes the meaning ("OTP …",
"If you …", "will be …") simply fails to match the layout, while text appended *after* a matched
layout does not change the recorded transaction.

**Payment method and bank derivation:**

- **Channel** (the payment method): `UPI, CreditCard, DebitCard, Wallet, NetBanking, ATM,
  IMPS, NEFT, RTGS, BankTransfer, Card` (generic card when credit/debit is unstated),
  `CashManual`. Credit vs debit card, wallets (Pluxee/Apay), net banking, and rail tags
  (`InfoRTGS*`, `UPI:` in ICICI/PNB account statements) are derived from the text.
- **Bank** — inferred from the SMS sender id, falling back to the body, via a shared
  `BankRegistry`. Senders that only issue credit cards (e.g. SBI Cards) promote a generic
  `Card` detection to `CreditCard`. The bank is stored per transaction.
- **Instrument** — only a **masked last-4** (`••••1234`) is ever captured. Full numbers,
  holder names, card networks and expiry are never parsed or stored.
- Recognized transactions are conservatively matched to any payment source you registered
  (`PaymentSourceMatcher`): missing evidence is allowed, contradictory evidence is not.

**Spend vs income (for the Spend Tracker home):**

- `spendByMethod` — **net spend per method type** = debits minus refund/reversal credits
  (refunds are netted against spend, never counted as income).
- `incomeByMethod` — genuine credits, excluding refunds/reversals.
- Both exclude self-transfers, card repayments, reversed originals, and non-successful or
  needs-review records, matching the ledger's monthly totals.

Parser coverage is limited to the layouts that have templates and is not guaranteed; see the
[remaining parser work](docs/ROADMAP.md#confirmed-open-parser-issues).

### Testing the parser

- **Unit tests** (JVM, synthetic inputs — `android-app/app/src/test`): parser classification
  and bank derivation (`PaymentMethodDerivationTest`), format coverage (`ParserCoverageTest`,
  `FinancialSmsParserTest`, `ResearchedFormatsTest`).
  ```sh
  cd android-app && ./gradlew testDebugUnitTest
  ```
- **Instrumented tests** (on a disposable emulator — `android-app/app/src/androidTest`):
  per-method spend and refund netting (`SpendAggregationTest`), and an opt-in end-to-end
  import→discovery run over a local SMS export (`PaymentMethodImportE2eTest`, skipped unless
  the export is present).
  ```sh
  cd android-app && ./gradlew connectedDebugAndroidTest
  ```
- **Local export harnesses** (opt-in, skipped when no export is present; they print results
  instead of asserting, so they are development tools rather than regression tests):
  - `ExportAuditHarness` (JVM) runs the real engine over an "SMS Exporter" text export and prints
    one tab-separated row per message (decision, amount, direction, channel, bank, rule id).
  - `ParserBenchmark` (JVM) and `ParserBenchmarkDeviceTest` (instrumented) measure parse cost per
    SMS and compare it against running every template against every message.

  Both JVM harnesses read `sms-history-sep2026.txt` at the repository root; the device benchmark
  reads the pushed `sms-history.txt` described below. Use `-i` to see their output, since Gradle
  hides test stdout by default:
  ```sh
  cd android-app && ./gradlew testDebugUnitTest --tests '*ParserBenchmark' --tests '*ExportAuditHarness' -i
  adb shell am instrument -w -e class in.txnsense.app.ParserBenchmarkDeviceTest \
    in.txnsense.app.test/androidx.test.runner.AndroidJUnitRunner
  ```
- **Debug file import** — on many emulators, SMS injected via `adb` are flagged "restricted"
  and hidden from a non-default SMS app, so the in-app import reads nothing. Debug builds add
  **Settings → SMS and past messages → "Import from test file (debug)"**, which parses an
  "SMS Exporter" text export through the real import pipeline. Push the file first:
  ```sh
  adb shell mkdir -p /sdcard/Android/data/in.txnsense.app/files
  adb push sms-history.txt /sdcard/Android/data/in.txnsense.app/files/sms-history.txt
  ```
  Use invented messages only; never commit a real SMS export (it is git-ignored).

## Known limitations

- English heuristic parsing, primarily INR; no measured production-accuracy guarantee.
- No iOS app, WhatsApp/Telegram/Discord capture, or automatic bank reconciliation.
- Refund/reversal linking and masked account hints cover supported formats only. Imported refunds/reversals require review.
- Monthly summaries exclude failed, reversed, pending, transfer and needs-review records. They are transaction summaries, not a verified account balance.
- History is paginated in 100-record pages; summaries are not verified bank balances.
- OEM background restrictions, multipart edge cases, permission lifecycle behavior, and real-phone reliability need broader testing.
- No automatic updater; check Releases for updates.

## Build from source

Use **JDK 17**, Android SDK platform **37.0** (compile SDK 37), and the checked-in **Gradle 9.5.0 wrapper**. Dependency versions are pinned in [the version catalog](android-app/gradle/libs.versions.toml). The first build downloads dependencies and requires network access on the build machine.

Open the `android-app` directory in Android Studio, or set `ANDROID_HOME` to your SDK installation and run:

```sh
cd android-app
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`. The APK is at `android-app/app/build/outputs/apk/debug/app-debug.apk`.

For connected Android tests, use a **dedicated disposable emulator**:
```sh
cd android-app
./gradlew connectedDebugAndroidTest
```
Instrumentation grants test permissions and creates/cleans synthetic records. Do not run it against a phone containing an important ledger. The host-driven SMS end-to-end test is skipped unless explicitly enabled; see [release verification](docs/RELEASING.md).

## Project layout

| Path | Purpose |
| --- | --- |
| `android-app/app/src/main` | Kotlin/Compose application, parser, storage and receiver |
| `android-app/app/src/test` | JVM tests using synthetic inputs |
| `android-app/app/src/androidTest` | Real Android storage, UI and capture checks |
| `android-app/app/schemas` | Versioned Room schemas for future migrations |
| `.github/workflows` | CI and manual alpha-release workflow |
| `docs` | Public privacy, roadmap and release guidance |

Private SMS backups, research datasets, evaluation workbooks, local configuration and signing keys are intentionally excluded.

## Releases and contributions

Development is **maintainer-led during alpha**. Bug reports and focused feedback are welcome now, using synthetic examples only. Broad external feature contributions will open when the app is ready for general use; discuss changes before starting a pull request. See [Contributing](CONTRIBUTING.md).

Releases use tags such as `v0.1.0-alpha.1` and an accompanying [changelog](CHANGELOG.md). The maintainer runs the release workflow to build a consistently signed debug APK, SHA-256 checksums and a draft pre-release, then reviews and publishes it. See [Releasing](docs/RELEASING.md).

## License

TxnSense's original source code is available under the [MIT License](LICENSE). Bundled dependencies retain their own licenses; see [third-party notices](docs/THIRD_PARTY_NOTICES.md).
