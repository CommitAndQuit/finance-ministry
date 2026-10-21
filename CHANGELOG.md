# Changelog

User-visible changes are recorded here. Pre-1.0 releases are experimental.

## Unreleased

## 0.1.0-alpha.24

- Add a clickable Home setup card for missing SMS and recording-notification
  permissions after the tour. Explain SMS access before asking; use permission
  prompts or Android settings as appropriate, refresh after grants/returning,
  and respect intentionally paused capture or disabled alerts. Not now snoozes
  the card for seven days; a new app version can offer setup again. Manual entry
  remains available without permissions.

- Recognize complete currency-less UPI debit alerts, including `debited by ...
  on date ... trf to ... Refno ...`, across issuers using that grammar. Extract
  the recipient and compact reference while retaining amount, OTP, future-payment,
  origin, multi-event and balance safeguards.
- Recognize named-recipient and VPA `Sent ... From ... To ... On ... Ref`
  payments across single-line and multiline alerts, Dr./Cr. UPI recipients,
  and `Spent ... using ... Credit Card no. ...` alerts with optional timestamps
  and line breaks. Exclude `Not done by you?` security instructions from payment
  evidence. Shared grammatical rules are issuer-independent (parser revision 14).
- Preview safe repairs for untouched, previously imported SMS records whose
  amounts were missing. Rescanning the original inbox can fill new parser evidence
  without adding duplicates or overwriting edits, dates, sources or linked records.
  Confirm repairs explicitly; recovered amounts remain in Review and out of totals.
- Enrich untouched imported debits with missing recipients or payment methods
  when the amount agrees. Field-only repairs preserve counted totals and review
  state, and never replace known sources, categories or user corrections.

## 0.1.0-alpha.23 — Play testing candidate (not published)

- Prepare a non-debuggable, minified Android App Bundle for Play testing with a
  new version code. Play App Signing retains the existing official-alpha signing
  identity; a separate upload key authenticates bundle uploads. This does not
  establish a tested Play-delivered upgrade or production/SMS-policy approval.

- Simplify named default categories to Grocery, Food, Entertainment, Bills,
  Transport and Investment, with Other for uncategorized payments. Preserve used
  older categories, custom labels, saved tools and encrypted-backup compatibility.
  Active category filters remain valid after their last matching payment changes.

- Publish an HTTPS privacy policy and add a link in Data and privacy while keeping
  the full policy available offline. If no browser opens, show a selectable URL
  and the support email without attaching any financial data.

- Accept fractional amounts without a leading zero, such as `.80` for ₹0.80,
  when recording or editing repayments and manual payments. Preserve exact paise,
  positive-amount validation and existing repayment allocation limits.

## 0.1.0-alpha.22

- Split group expenses by parts (including fractional or zero parts), with exact
  paise allocation, per-person previews and reusable ratio defaults. Keep equal
  and custom-amount splits and the existing individual repayment safeguards.
- Make the existing optional transaction notes easier to find as Description in
  the main payment editor; existing descriptions remain searchable and encrypted.
- Add Help & feedback in Settings with bug-report and feature-request email
  drafts addressed to financeministrysupport@gmail.com, plus copy-address fallback.
  Only the app version and a blank report template are included automatically.

- Recognize ICICI UPI debit alerts whose recipient name contains digits, so a
  single outgoing payment is recorded instead of sent to Review for conflicting
  debit/credit wording. Preserve checks for ambiguous and negated movements
  (parser revision 12).

- Replace the generic green launcher badge with an ink-blue FM monogram, adaptive
  launcher shapes and Android themed-icon support. Notification glyphs stay separate.

- Detect Federal Bank One Credit Card merchant refunds phrased as already
  received, even when a following sentence says they will be adjusted against
  outstanding dues. Preserve the merchant and INR amount; unmatched refunds
  remain in Review rather than being silently linked to a purchase. Future,
  negated and pending receipts retain conservative handling (parser revision 11).

- Verified the feature changes with 183 JVM tests and 34 focused Android 16
  emulator checks covering split calculations, saved defaults, settlements,
  descriptions, support drafts and editor usability. Local lint reported zero
  errors and 61 warnings. Release CI runs the wider instrumentation/SMS gates.
  No database schema change from alpha.21. Back up important ledgers before
  updating; existing transactions are not silently reparsed. This remains a
  debug alpha, not a Play Store production release; physical/OEM validation is
  not claimed.

## 0.1.0-alpha.21

- Refresh the native app around Home, Activity, People and Review with warm light
  surfaces, clearer dark-theme contrast, quieter fields and focused editing sheets.
  Keep category/purpose editing short, place advanced fields under More details,
  and keep the sheet heading, Close and primary action reachable while scrolling
  or using the keyboard. Existing sources, import, budgets, reminders and widget
  remain available.
- Explain the selected month's actual spending with a numerical breakdown. Keep
  personal/family spending and your group share separate from gross money out;
  show money in, refunds and outstanding repayments with consistent eligibility
  rules. Activity matching totals use the same accounting rules as Home.
- Save reusable local groups with equal or custom default ratios. Preview exact
  shares before applying; changing a preset does not rewrite recorded expenses.
  Preserve existing individual settlements and protect linked repayment records
  from incompatible financial edits.
- Make repayment recording a focused choice between an existing recorded payment
  and cash received. Explain possible matches, require explicit confirmation,
  retain draft amounts when returning from the payment picker, and avoid creating
  duplicate incoming payments. Repayments do not change the original personal share.
- Distinguish named people from unnamed/legacy balances on Home and People. Fix
  hidden group/self-transfer draft state when explicitly changing money out to
  ordinary money in, without bypassing protections on existing repayments.
- Simplify Settings, retain System/Light/Dark appearance in encrypted backups,
  update the skippable app tour, and surface actionable capture/permission warnings
  and dismissible backup reminders. A capture warning is not cleared merely by
  adding a manual payment.
- Expand bounded financial-SMS layouts and conservative reference/source handling
  in parser revision 10. Keep uncertain origins, secondary confirmations and
  ambiguous movements reviewable; label card-bill confirmations without presenting
  them as income. Existing records are not silently reparsed. This does not
  authenticate senders or establish support for every bank template.
- Add a non-destructive Room schema 8→9 migration and encrypted backup support for
  saved group presets. Older supported backups are upgraded during validation;
  schema-9 backups require a schema-9-capable release and cannot be restored by
  the published alpha.20. Create an encrypted backup before upgrading and do not
  downgrade an updated installation. CSV remains an unencrypted report, not a restore backup.
- Align source-visible typography, spacing, navigation, choice chips and row
  separators with the approved HTML/CSS proposal, keeping native accessibility
  scaling and existing financial controls. The styling follow-up passed 173 JVM
  tests and 30 focused emulator checks in each of light and enlarged-text dark
  configurations; lint reported no errors and 58 warnings. This is not proof of
  an exact match against a rendered reference.
- Release verification is still in progress. Local debug builds are not official
  alpha downloads. Design acceptance uses the approved HTML/CSS/JS source comparison;
  pixel-perfect rendered parity is not claimed. The previous official APK's signed
  in-place upgrade remains unverified. Physical/OEM testing is deferred
  for this debug alpha at the owner's request, not claimed as completed. It remains
  important before wider distribution. No production-store readiness or general
  parser-accuracy claim is made.

## 0.1.0-alpha.20

- Split a repayable Group expense between You and named local people. Equal splits
  preserve every paise; Custom splits must total the expense exactly. Reuse or rename
  nicknames without contacts access, invitations or cloud sync.
- Show individual Outstanding/Settled balances and record partial/full settlements
  against one person. Link an existing incoming payment without counting income twice,
  or record a cash receipt. Keep Save/Apply above the keyboard and protect unsaved drafts.
- Preserve older unnamed balances without guessing participants. Unlink existing
  repayments before reallocating shares; unlinking keeps the incoming payment record.
- Add non-destructive Room schema 7→8 migration and validated encrypted backup support
  for people, shares and individual settlements. Older schema-7 backups remain readable;
  backups created by this release require alpha.20 or later. Do not downgrade an updated
  installation. CSV schema 3 includes participant names, shares and settlement amounts;
  it is an unencrypted report, not a restore backup.
- Prevent an untouched transaction editor from dismissing/flickering when scrolling
  downward at the top. Retain the current palette and existing transaction accounting.
- Verification: 135 JVM tests; 16 targeted data/migration/backup/export checks; eight
  group UI flows in normal light and compact dark/larger-text emulator configurations.
  The compact-screen rendering crash found during validation was fixed and rerun.
  CI verification is required before publication. Signed artifact upgrade/recovery and
  physical-device/OEM checks remain separate gates; this is a debuggable testing alpha,
  not a Google Play or general-use production release.

## 0.1.0-alpha.19

- Simplify money owed: record money received directly from an outstanding expense,
  search recorded incoming payments in a bounded picker, or record a cash receipt.
  Keep Save above the keyboard, show the remaining amount before saving, and tuck
  additional monthly metrics into Month breakdown. Repayment accounting and
  existing receipt/edit/unlink protections remain unchanged.
- Simplify transaction entry: keep Category and spending purpose prominent, move
  the technical transaction type to Advanced details, and offer plain-language
  card bill, refund and own-account transfer actions. Ordinary unidentified
  payments can be confirmed without a type dropdown; unidentified card credits
  still require an explicit accounting choice. Existing accounting types and
  financial calculations remain unchanged.

- Verification: targeted Android 16 form/repayment regression checks, dark/larger-text
  repayment checks, JVM tests, lint and APK assembly. Physical-device and
  OEM-background delivery validation remain pending. This remains a debuggable
  GitHub alpha, not a Google Play production release.

## 0.1.0-alpha.18

- Replace the short first-use checklist with a guided App tour covering recording,
  totals, transactions and batch edits, categories and purpose, breakdown and
  budgets, review, repayments, sources and card bills, rules, reminders, widget,
  historical import, backup/export, personalization and privacy.
- Offer the tour on first launch and once after every app update. Skip at any
  chapter, use Back/Next, expand More detail, or replay from Settings. Optional
  setup links never enable SMS capture or request permissions automatically.
- Keep existing transactions, accounting, database schema and user settings
  unchanged. Tour progress is local to the installation, not imported from backups.
- Known limitations: this is still a debuggable GitHub alpha, not a Google Play
  production release. Supported SMS formats and device background restrictions
  remain subject to the existing parser and physical-device testing limitations.

## 0.1.0-alpha.17

- Reject more explicitly negated SMS movements and keep conversational, conditional
  and dispute-reference candidates in Review rather than confirmed spending. Valid
  purchase alerts with security/dispute instructions remain supported. Parser v8
  does not rewrite existing records or authenticate bank senders.
- Offer direct Android SMS-permission settings recovery after permanent denial;
  refresh capture status after granting or revoking permission. Manual entry remains available.
- Improve large-text headers, wrapping navigation and Add-button padding. Keep
  Settings navigation visible while scrolling long content and returning between sections.
- Add offline-readable privacy guidance, independent/non-government positioning and
  clear explanations for exported files, backup passwords and SMS delivery dates.
- Expand CSV schema 2 with status, source/type, own share, linked IDs, exclusion reasons
  and dashboard contribution amounts so exported reports can be reconciled. CSV is not a backup.
- Add regression coverage and an Android 16 CI gate with host-driven synthetic SMS
  recording/notification validation before draft alpha releases.
- Known limitations: this remains a debuggable testing alpha. Physical Redmi/OEM,
  16 KB runtime, official signed upgrade/recovery and Google Play readiness are
  separate gates. Local minified-build and fresh-install recovery checks used a
  disposable test signer, not the official distribution certificate.

## 0.1.0-alpha.16

- Recognize merchant-first OneCard/Federal purchase alerts, including "Fresh picks!"
  messages without a transaction date, preserving merchant and masked card details.

- Label card-bill settlements clearly in transactions, details, import previews and
  notifications instead of presenting issuer confirmations as money received.
- Recognize more explicit card-bill receipts and bank-side bill-payment confirmations;
  refunds, cashback and generic debits are not automatically reclassified.
- Add a confirmed "Mark as card bill payment" action and an accessible add/edit option.
  The shortcut preserves amount, status and review state and records a correction.
- Add "Card bill payments" and "Card credits to check" filters. Existing records are
  not silently rewritten; raw SMS is not retained for retrospective parsing.

## 0.1.0-alpha.15

- Add built-in Income and Investment transaction categories and a way to create
  persistent, local custom categories while editing or adding a transaction. Custom
  categories work in filters, rules, batch edits, budgets, reminders and encrypted backups.
- Categories remain labels: money-in/out and spending calculations are unchanged.
  Alpha.15 restores older backups; its new-format backups require alpha.15 or later.
- Known limitation: SMS capture and OEM-specific notification behavior still need
  physical-device validation; parser output must be reviewed for misses or errors.


## 0.1.0-alpha.14

- Bring the approved next-chapter design into the native app: a calmer Overview,
  in-place Transactions selection, a focused add/edit sheet, and clearer Review
  and Settings navigation.
- Open a spending category in the filtered Transactions list, with personal share
  and gross money out labelled separately. Keep filtered result totals available
  on demand rather than as a large permanent card.
- Make category and purpose editing reachable from transaction details, and put
  search inside transaction filters without changing how records are stored.
- Improve backup, capture-health, budget and reminder presentation while keeping
  their existing data and permission behavior.
- Preserve an unsaved edit draft when Android recreates the activity during the
  transaction sheet. A dirty form cannot be dismissed by an accidental swipe.
- Known limitation: SMS capture and OEM-specific notification behavior still need
  physical-device validation; parser output must be reviewed for misses or errors.

## 0.1.0-alpha.13

- Add dated repayment history with partial cash or linked incoming repayments,
  explicit repayment-expected handling, gifts/treats, person/group summaries and
  separate all-time versus selected-month amounts owed.
- Add password-encrypted local backup and atomic replace restore, restore preview,
  CSV report export, and portable coverage for transactions, sources, rules,
  repayment history, match decisions, budgets and reminders.
- Add conservative Possible matches review for duplicate records and own-account
  transfers. Decisions keep both records, preserve an audit trail and can be undone.
- Add remembered merchant category rules, conflict review and explicit selected-row
  batch cleanup with previews and undo.
- Add a personal-share spending breakdown with fair prior-period comparison,
  category drill-down, a separate refunds-received subtotal and category budgets.
- Add Capture health diagnostics that distinguish SMS permission, capture,
  notifications and only the recording errors the app actually observed.
- Add optional local category budgets, monthly payment reminders that never pay or
  record automatically, and a privacy-first home-screen quick-add widget.
- Add formal Room schema 4→7 migration verification and bounded possible-match
  scanning for large imported histories.

## 0.1.0-alpha.12

- Refine the ledger into the approved Quiet Ledger presentation with calmer
  surfaces, smoother rounded cards, lighter dividers, clearer dark-theme contrast,
  flatter transaction rows and a compact Add transaction action.
- Make the Overview summary explicitly describe **Your share of spending** and
  clarify how personal, family and group shares contribute to it.
- Make filtered transaction-result totals use the same confirmed-payment rules as
  Overview, excluding review items, failed payments, self transfers, card
  repayments and reversed originals from Money in and Money out.
- Add multi-select filtering by registered payment source, including inactive
  sources, alongside the existing direction, purpose, origin and category filters.
- Improve transaction-result labels and helper text so filtered totals are easier
  to compare with the whole-month Overview.
- Preserve the first-use welcome flow without showing a duplicate Add action.

## 0.1.0-alpha.11

- Redesign the ledger around three focused destinations: **Overview** for the
  selected month, **Transactions** for search and filters, and an all-dates
  **Review** queue for uncertain records.
- Add transaction search plus clearly scoped Money out and Money in subtotals
  for the current result set. These do not change the Overview's monthly totals.
- Add an optional on-device preferred name for the short home greeting.
- Make category/purpose updates available directly from transaction rows while
  preserving an uncertain record's review state.
- Add multi-select category filters combined with money flow, purpose and origin.
  Filters narrow transactions without changing the whole-month dashboard totals.
- Add Flat expenses as a transaction category, separate from payment purpose.
- Add Family as a payment purpose and transaction filter. Family expenses count
  toward your spending and do not create repayment balances.
- Align the native ledger with the approved calm-ledger design: a dominant monthly
  spending summary, separate all-date amount owed, an explicit review task, and
  lighter divider-separated transaction rows.
- Keep manual entry focused on amount, label, category and payment purpose; date,
  source and notes remain available under More details.
- Make first-run setup clearly optional and keep adding a transaction immediately
  available from the welcome card.

## 0.1.0-alpha.10

- Add Money out and Money in transaction filters. They work together with the
  existing purpose and origin filters.

## 0.1.0-alpha.9

- Show selected-month money in and money out on the dashboard; open the totals
  explanation from an accessible info button.
- Add a notification Categorize shortcut with category, payment purpose, and group
  share fields together. Label changes preserve financial details and review status.
- Wrap payment-purpose choices so every option is discoverable on narrow screens.

## 0.1.0-alpha.8

- Complete a review-response release hardening pass before packaging:
  - preserve exact timestamps when users edit only non-financial fields,
  keep imported/self-transfer records stable, and make source assignment safer for
  manual and imported entries.
- Add source-management UX and guided onboarding:
  - user-registered payment sources, per-transaction source attribution,
  and an explicit first-use guide.
- Improve reminders and review workflows with deterministic scheduling, cancellation
  and state refresh after app resume.
- Tighten parser behavior for common edge cases from Indian bank/card SMS, including
  known ICICI, BOB, Kotak, UPI and card variants; uncertain/unsupported forms stay
  in Review instead of silently auto-saving.
- Fix repayment/foreign-state presentation and unknown status handling, including
  improved totals behavior and clearer debt expectations.
- Add large-font/filter UX polish and additional regression coverage for
  repository and receiver flows.

## 0.1.0-alpha.6

- Parser version 6 adds researched Indian card/account-alert variants, including
  Kotak date-before-merchant, BOBCARD, SBI, Federal, IDFC FIRST and YES Bank card
  payments. Balance, available-limit, outstanding and cycle-total amounts are ignored.
  Unsupported foreign-currency card alerts and unfamiliar card layouts require review.
- Add regression cases for selected public-format variants across PNB, IndusInd,
  Indian Bank, NSDL, Punjab & Sind Bank, Kerala Bank and Bank of India. This is not
  a claim of complete bank coverage; source provenance and negative variants are
  recorded in the roadmap.
- Validated the three reported Kotak and BOBCARD alerts in the Android emulator.
  Automated evidence does not establish physical-device reliability or complete bank coverage.

## 0.1.0-alpha.5

- Add opt-in last-three-calendar-month SMS import in Settings, with separate inbox
  permission/disclosure, normalized preview, confirmation, duplicate checks,
  original receipt dates, cancellation and batch undo preserving edits. No import
  notification flood or raw SMS storage. Add non-destructive database migration 2→3.

- Recognize the bounded ICICI masked-account debit / named-recipient credit UPI
  template as one outgoing payment. Preserve conservative handling of mixed events,
  incoming credits and OTP/scheduled/negated messages.
- Expand bounded recognition for BOB Dr./Cr. payments, Axis and ICICI card spends,
  HDFC card-UPI/received/deposited/mandate alerts, Pluxee wallet spends and fees,
  posted and initiated refunds, card repayments and secondary payment receipts.
- Require movement evidence instead of treating credit/debit card product names
  or standalone processing text as transactions. Support compact INR amounts,
  single-digit wallet timestamps and balance/limit labels containing "is".
- Classify card repayments separately and exclude them from recorded totals.
  Keep supported merchant receipts and card-autopay confirmations in review to
  avoid automatically double-counting a corresponding bank/card alert. This is
  conservative review routing, not cross-source automatic reconciliation.
- Parser version is now 5. Existing saved records are not automatically changed;
  previously misclassified repayments can be corrected using Card Repayment.
- Old SMS are read only through
  the explicitly confirmed import flow; existing saved records are not reprocessed.
- Automated validation: 35 JVM tests and 24 distinct emulator tests passed before
  release preparation. Manual/physical-device testing is waived for this debug alpha,
  not claimed as completed. Install over the previous official alpha; do not uninstall.

## 0.1.0-alpha.4

- Separate Home from Settings, with recorded totals, date-grouped history and
  clearer review prompts. Keep existing 100-record history pagination.
- Simplify manual entry with money-in/out choices, native date/time pickers,
  optional advanced details, a keyboard-aware Save bar and a discard warning.
- Introduce a neutral teal light/dark theme and readable transaction labels.
  Widgets and general-use readiness are not part of this increment.

- Recognize structured `Spent Rs… On … Card … At … On …` and amount-first
  card-spend messages as card merchant debits, with masked last-four hints and
  merchant labels. Reject standalone spend chatter, offers and OTP messages.
- Exclude inline as well as newline-separated `Not You?` security instructions
  from transaction field inference. Preserve review handling for multiple amounts.
- Add an opt-in parser evaluation harness that loads private inputs externally;
  no message corpus is bundled in the app or committed to the public repository.

Known limitations: messages describing both the user's account being debited and
the recipient being credited can still be rejected. Add missed transactions manually.
Parser coverage and PRD accuracy targets are not independently human-verified.
Physical-device testing, widgets, progressive history loading and field-level
validation polish remain pending. No raw SMS history is retained for reprocessing.
Install over the previous official alpha; do not uninstall an important ledger.

## 0.1.0-alpha.3

Includes the alpha.2 improvements below and fixes a small-screen layout issue:
the ledger page scrolls and reserves visible height for transaction history instead
of letting the controls shrink it to zero. Alpha.2 was withheld as an unpublished
draft after this issue was found during final artifact verification.

## 0.1.0-alpha.2 (unpublished draft)

### Parser reliability

- Recognize structured sent-payment messages with a masked source account and recipient.
- Exclude available-balance abbreviations from transaction amounts, without discarding
  a later transaction amount simply because a balance appeared earlier.
- Preserve known direction, status and channel when transaction amounts are ambiguous.
- Extract masked account hints and conservative recipient labels from supported templates;
  persist those labels in the encrypted ledger.
- Exclude the structured "Not You?" security footer from channel inference.
- Add synthetic parser and encrypted-storage regressions. Existing SMS records are not
  reprocessed; manual corrections remain authoritative.
- Keep the SQLCipher connection password valid until database close so additional
  pooled connections can open; still clear temporary input buffers immediately.

### Ledger completeness

- Browse all saved records in 100-record pages with stable ordering for equal timestamps.
- Apply review/manual/edited filters before pagination so older matches remain accessible.
- Show today's debit and credit totals alongside monthly totals, independently of the
  current history page or filter. Exclude transfers and non-final/review records.
- Verify history beyond 500 records and daily boundaries, edits and deletions using
  synthetic records in isolated encrypted test databases.

### Refund/reversal links

- Link full-amount adjustments only with one eligible original sharing an explicit
  reference hash, SMS sender, masked account and known channel. Ambiguous, partial,
  differently-sourced or unmatched adjustments stay reviewable.
- Show a link to the original record. Linked reversals exclude the original debit
  from totals; refunds remain separate debit/credit entries, not net spending.
- Remove automatic links on edits/deletions; never change a user's corrected fields.
- Migrate encrypted schema 1 to 2 by adding nullable fields, preserving old records
  and corrections. Older records have no reference hashes and cannot be backfilled
  because raw SMS is not retained.

### Permission and navigation reliability

- Refresh capture/notification availability and ledger totals when the activity resumes.
- Explain blocked Android notifications and provide a notification-settings shortcut;
  capture and manual entry remain independent of notification availability.
- Consume notification navigation requests so they do not replay after recreation.
- Handle deleted-record notification actions without leaving a stale edit form open.
- Return to the newest history page after saving a manual entry or correction.
- Verify notification PendingIntent targeting/recreation, deleted-record handling,
  notification failure isolation, and background system-SMS capture on the emulator.
  OEM behavior and real-device latency still require validation.

### Alpha.2 candidate validation

- Add isolated encrypted-storage concurrency checks: duplicate deliveries produce
  one save callback, corrections survive concurrent duplicate capture, simultaneous
  edits preserve a complete audit chain, and capture/erasure races leave no database
  or keys behind. New explicit manual entry remains usable after erasure.
- Candidate version `0.1.0-alpha.2` / code 2 uses the persistent alpha signing identity.
- In-place installation over the published alpha.1 APK passed on an isolated Android
  16 emulator without uninstalling. Edited record, correction history, encryption keys
  and schema migration survived, including a subsequent cold process restart.
- Physical-device validation remains pending; emulator results are not an OEM guarantee.
- Verify 20 synthetic three-segment SMS deliveries, each producing one transaction
  and a notification, on the isolated Android 16 emulator. Observed median/p95/max:
  149/217/1,830 ms without a concurrent build. An earlier run exceeded five seconds;
  this sample does not close the real-device latency gate.
- Verify SMS restarts an ordinarily killed background app process and saves/notifies
  a credit while preserving the pre-existing upgraded ledger record. Force-stop,
  reboot and OEM battery restrictions are not covered by this check.

## 0.1.0-alpha.1

First open-source debug alpha.

### Added

- On-device parsing of new incoming financial SMS with visible review states.
- Encrypted Room/SQLCipher ledger and Android Keystore-protected secrets.
- HMAC source deduplication and save-before-notify native View/Edit notifications.
- Manual add, edit/confirm, correction history, delete and erase-all flows.
- Monthly summaries and filters for manual, review and edited records.
- Public build instructions, privacy documentation, contribution policy, CI and release workflow.

### Validation and limits

- Initial core verified with 10 JVM tests and 7 Android 16 emulator tests, including a
  synthetic system-SMS-to-record-and-notification path and real manual entry.
- Release/build CI results are linked from GitHub Actions; do not interpret JVM CI as
  a substitute for physical-device testing.
- Debug build only. No production accuracy guarantee, historical SMS import, backup,
  export, automated refund linking or verified physical-device support yet.
