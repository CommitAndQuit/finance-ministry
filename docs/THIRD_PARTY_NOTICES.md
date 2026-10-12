# Third-party notices

The MIT license covers Finance Ministry's original source. Dependencies retain their
own copyrights and licenses. This file and the license texts in
`android-app/app/src/main/assets/licenses/` are included with downloadable APKs.
The texts are also embedded in the APK's assets.

| Component | Upstream | License |
| --- | --- | --- |
| AndroidX / Jetpack (Compose, Activity, Navigation, Room, SQLite and supporting libraries) | https://android.googlesource.com/platform/frameworks/support/ | Apache-2.0 |
| Kotlin standard library and kotlinx.coroutines | https://github.com/JetBrains/kotlin and https://github.com/Kotlin/kotlinx.coroutines | Apache-2.0 |
| SQLCipher for Android 4.17.0 | https://github.com/sqlcipher/sqlcipher-android/tree/v4.17.0 | BSD-style, see SQLCipher-Android.txt |
| SQLCipher core | https://github.com/sqlcipher/sqlcipher | BSD-style, see SQLCipher-Core.txt |
| SQLite (within SQLCipher) | https://sqlite.org/copyright.html | Public domain |
| LibTomCrypt (native cryptography within SQLCipher) | https://github.com/sqlcipher/libtomcrypt | Public-domain option of its dual license; see LibTomCrypt.txt |

SQLCipher's pinned Android source references core revision
`e2a6040f2ae5cfff2b3e08eb3320007d93cdf3fc` and LibTomCrypt revision
`476a9579ae94f32b9ea9e2747bfb04b302370259`.

AndroidX is copyright The Android Open Source Project; Kotlin is copyright JetBrains
and contributors. Full Apache-2.0 terms are in `Apache-2.0.txt`.

Build/test tools are separate from the application: Gradle and the Android Gradle
Plugin use Apache-2.0; JUnit 4 uses EPL-1.0. Consult their distributions for full notices.
The dependency catalog pins direct versions; inspect the complete resolved graph with
`./gradlew :app:dependencies --configuration debugRuntimeClasspath` when updating.

When adding or upgrading a dependency, review its license and native/transitive
components and update these notices before distributing a new APK. This list does
not change or replace the upstream license terms.

## SMS format research acknowledgements

The [Indian Payment SMS Corpus](https://github.com/skyaara/indian-payment-sms-dataset),
by SplitEasy contributors, was used locally for synthetic parser evaluation and
format research at revision `ef81c756ddd8c8f5ba09f1b00820700108f9c84c`.
Its authored synthetic dataset and documentation use
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/); consult its `SOURCES.md`
for provenance and upstream credits. Regression messages use invented values and
identifiers, and the parser implementation is independently written. No upstream
parser implementation or full corpus is bundled in the app. This synthetic
evaluation is not real-inbox accuracy or bank certification.
