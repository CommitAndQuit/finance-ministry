# TxnSense

An offline, privacy-first Android expense tracker that records transactions from incoming financial SMS and lets you organize, track, and manage your spending locally.

**Key Highlights:**
- **100% Offline & Private:** Zero Internet permission (`android.permission.INTERNET` is not requested). No accounts, cloud sync, analytics, or trackers.
- **On-Device Storage:** Encrypted database (Room + SQLCipher) with keys managed securely via Android Keystore.
- **Deterministic SMS Parsing:** Fast, on-device template matching without machine-learning bloat or external API calls.

---

## Roadmap

### Completed Features

- [x] **Automatic Financial SMS Capture**: On-device capture for UPI, Credit/Debit cards, NetBanking, Wallets, ATMs, and Bank Transfers.
- [x] **Deterministic Template Engine**: ~90+ bank layouts with sender-based bank and payment channel derivation.
- [x] **Manual Transaction Management**: Add, edit, categorize, or delete cash and digital transactions with custom timestamps and tags.
- [x] **Spend & Income Tracking**: Daily, monthly, and per-payment-method spend analytics with refund netting.
- [x] **Transaction Review Queue**: Safeguards uncertain amounts or unrecognized formats for manual verification before booking.
- [x] **Past SMS Import**: Optional on-device scan of the last 3 months of inbox messages with safety previews and undo support.
- [x] **Encrypted Local Storage**: Hardware-backed key protection using SQLCipher and Room database migrations.
- [x] **Modern Compose UI**: Material 3 UI featuring light/dark themes, responsive layouts, and unsaved-changes warnings.
- [x] **Automated CI/CD**: GitHub Actions workflow for automated APK building, tagging, and releases.

---

### In Progress: F-Droid Integration

The current priority is enabling distribution via F-Droid so users can install and receive seamless updates without relying on Google Play Services or sideloading prompts.

#### Action Items to Complete F-Droid Integration:

##### 1. Self-Hosted F-Droid Repository (GitHub Pages)
- [x] **Configure Repository Settings**: Configured [`fdroid/config.yml`](fdroid/config.yml) with repository metadata, URL (`https://commitandquit.github.io/finance-ministry/fdroid/repo`), and 512x512 branded [`fdroid/icon.png`](fdroid/icon.png).
- [x] **Automate GitHub Actions Workflow**: Added [`.github/workflows/fdroid-repo.yml`](.github/workflows/fdroid-repo.yml) to index releases and deploy to GitHub Pages with landing page and `fdroidrepo://` badge.
- [ ] **Generate Persistent Keystore**: Add `FDROID_KEYSTORE_BASE64` and `FDROID_STORE_PASSWORD` in repository Actions secrets (workflow currently uses auto `fdroid init` fallback).
- [ ] **Enable GitHub Pages**: Set repository Pages source to **GitHub Actions** in GitHub settings.
- [ ] **Test Client Subscription**: Verify adding `fdroidrepo://` and installing/updating the app using the official F-Droid client.

##### 2. Official F-Droid Catalog Submission (`fdroiddata`)
- [x] **Fastlane Metadata**: Added `fastlane/metadata/android/en-US` containing app summaries, description, and icon.
- [x] **Prepare Build Recipe**: Added [`fdroid/metadata/in.txnsense.app.yml`](fdroid/metadata/in.txnsense.app.yml) ready for upstream submission to `gitlab.com/fdroid/fdroiddata`.
- [ ] **Screenshots**: Capture and add 3-5 app screenshots to `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- [ ] **Local Build Verification**: Validate the build recipe using `fdroid build` inside an isolated container.
- [ ] **Submit Upstream MR**: Open and track a Merge Request on `gitlab.com/fdroid/fdroiddata`.

---

## Build from Source

Requirements: **JDK 17** and **Android SDK 36**.

```bash
cd android-app
./gradlew assembleDebug
```

Run unit tests:
```bash
./gradlew test
```

---

## License

This project is licensed under the [MIT License](LICENSE).
