# Privacy and data handling

Updated 20 October 2026. TxnSense is an independent app
maintained by hk121902-stack and CommitAndQuit, not a government service or a bank. Support is
available at [financeministrysupport@gmail.com](mailto:financeministrysupport@gmail.com);
share synthetic examples only. Use the private reporting route in SECURITY.md
for vulnerabilities. The app includes offline-readable privacy information under
Settings → Data and privacy, alongside a link to the
[public privacy policy](https://hk121902-stack.github.io/finance-ministry/privacy.html).

TxnSense processes incoming SMS on the Android device to create a local
transaction ledger. There is no sign-up or app backend.

## Permissions

`RECEIVE_SMS` is optional and requested only after an in-app disclosure. Android
delivers incoming messages broadly, including non-financial messages; the app filters
them on-device and rejects OTP/verification and non-transaction messages. It does not
request `SEND_SMS`, contacts, notification-listener access or Internet.

From alpha.5, builds also offer optional `READ_SMS` access, requested only after
a separate historical-import disclosure. Android grants broad inbox access; the app
queries only the last three calendar months and filters messages locally. The preview
keeps normalized financial fields in memory until confirmation, cancellation or leaving
Settings. Import saves those fields and batch metadata, never raw messages or senders.
It does not change your phone's SMS or send individual import notifications. Undo removes
untouched records in a batch and preserves user edits. Erase all clears import previews
and invalidates scans. Alpha.4 and earlier do not include this permission or feature.

`POST_NOTIFICATIONS` is separate. Disabling notifications does not disable otherwise
authorized capture. Manual entry works without either permission. The in-app capture
toggle can pause new recording without revoking Android's permission.

## What is stored

The encrypted local database stores normalized amounts, currency, direction, status,
channel/category, timestamps, source type, parser version/confidence, review state,
masked account hints, user corrections and correction history. A keyed HMAC fingerprint
detects repeat delivery without retaining its source inputs. Labels and notes you
enter yourself are also stored locally. From alpha.2, the app additionally stores supported
parsed recipient labels, device-keyed hashes of explicit transaction references, and
local record IDs linking high-confidence refunds/reversals. Raw reference numbers are
not stored. Editing or deleting a record removes affected automatic links.

Raw SMS bodies and sender values are processed transiently and are not database
columns. No raw-message debug logging or upload feature is included. Input filtering
cannot prove arbitrary manually entered text is non-sensitive; do not paste messages,
contact names, full account numbers, UPI IDs or OTPs into labels or notes.

The local group-split feature stores optional reusable participant
nicknames, stable local IDs, individual shares and settlement allocations in the
encrypted ledger. Use nicknames, not contact details. It never reads contacts,
invites people or syncs balances. Encrypted backups include these records; CSV
schema 3 adds participant names, shares, received amounts and unpaid amounts.
Those CSV fields are readable by anyone who can access the exported file.

## Learning new message formats

From alpha.11, an optional setting lets the app work out its own parsing rules for bank
alert formats it cannot read. It is off unless you turn it on, and it needs no additional
permission: it only ever sees messages the app already receives. There is still no Internet
permission and nothing is sent anywhere; all of this runs on the device.

When the setting is on and a message no rule can read arrives, the app stores an outline of
its format — the recognized banking words in order, plus a typed blank recording that a value
of some length stood in each remaining position. Amounts, account numbers, merchant and payee
names, references, dates, links and every word outside the app's banking vocabulary are
already blanks before anything is written, so the stored outline cannot be turned back into
the message. Outlines are stored in the same encrypted database, keyed by a hash of the
outline, so repeated arrivals of one format increment a counter rather than adding rows. At
most 400 distinct outlines are kept; an outline unseen for 90 days is deleted.

A derived rule is not used as soon as it exists. It first reads live messages while recording
nothing, and only after five clean reads may it create transactions — always as records marked
for your review, never recorded automatically, at any age. If it ever reads a message
differently from a rule that shipped with the app, it is discarded permanently and is not
derived again. Rules that ship with the app always take precedence. The settings screen lists
every derived rule, its stage and what it was derived from, and can delete any of them.
Switching the setting off deletes every outline and derived rule; so does Erase all.
Derivation runs in the background only while the device is idle and charging.

SQLCipher encrypts the database. Android Keystore protects a wrapping key and source
HMAC key; the wrapped database password is held in private app preferences. Database
and preferences are excluded from Android backup rules, including device transfer.
This is configuration and emulator evidence, not a guarantee about every OEM's backup implementation.

## Notifications and deletion

Notifications contain normalized transaction details, not the source SMS. Optional
budget and recurring-payment notifications contain only the configured category,
reminder name and normalized amount. A reminder never initiates a payment or records
a transaction. Private
lock-screen visibility is requested; review your device's notification settings.
Android's own Messages app can independently show the original SMS.

Deleting a transaction also removes its correction history. Erase all removes the
database, keys and settings and pauses capture. Uninstalling removes the local ledger.
The app can create a user-chosen password-encrypted backup through Android's file
picker and can restore that file after validation and a preview. The password and
file are not uploaded or recoverable by the app. A CSV export is a readable report,
not a complete restore backup, and is unencrypted once written to the location the
user chooses. Restore replaces the local ledger atomically and pauses SMS capture and
reminders for explicit reconfirmation. Database key or restore errors preserve the
existing ledger rather than silently replacing it.

The chosen file provider may sync exported files under its own policies. Erasing
or uninstalling the app does not delete files you exported. CSV schema 2 includes
status, transaction type, exclusion reasons and dashboard contribution fields;
it remains an unencrypted report, not a restore backup.

## Debug alpha

Official alpha downloads are debuggable. Someone with an authorized debugging
connection can inspect the app; use sample data and a test device. No production
security certification or regulatory approval is claimed.

## Support email

Contact **financeministrysupport@gmail.com** for bugs or feature requests through
Settings → Help & feedback. This opens your email app with the app version and
a blank report template. You review and send the email yourself. The app does not
attach transactions, SMS, logs, backups or screenshots. If no email app is available,
you can copy the support address and use another email service. Your email provider
handles the information you choose to send under its own policies. Remove private
details from any screenshots or examples you add.

GitHub Pages hosts the public policy. GitHub handles visits to that page, the
repository, issues and downloads under its
[privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
Hosting the policy does not upload the app's ledger or give the app Internet access. Only
share synthetic examples when reporting a bug. See [Security](../SECURITY.md) for
private vulnerability reports.
