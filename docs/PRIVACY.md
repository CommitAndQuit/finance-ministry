# Privacy and data handling

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

Notifications contain normalized transaction details, not the source SMS. Private
lock-screen visibility is requested; review your device's notification settings.
Android's own Messages app can independently show the original SMS.

Deleting a transaction also removes its correction history. Erase all removes the
database, keys and settings and pauses capture. Uninstalling removes the local ledger.
There is no restore, export or recovery service in this alpha. Database key errors
preserve existing files rather than silently replacing the ledger.

## Debug alpha

Official alpha downloads are debuggable. Someone with an authorized debugging
connection can inspect the app; use sample data and a test device. No production
security certification or regulatory approval is claimed.

GitHub handles repository visits, issues and downloads under its own policies. Only
share synthetic examples when reporting a bug. See [Security](../SECURITY.md) for
private vulnerability reports.
