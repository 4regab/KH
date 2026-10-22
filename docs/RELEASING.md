# Android releases

Use JDK 17, Android platform 35, build tools 35.0.0, and the checked-in Gradle wrapper.
`bash ./gradlew clean`, `bash ./gradlew test`, `bash ./gradlew lint`, and
`bash ./gradlew assembleRelease` must all pass. Tests must never be disabled to publish.

## Signing and upgrades

The published v3.2 APK uses an Android Debug certificate, SHA-256:
`9f111ae130b036c5a6b628274f0c0f9c4e7de5370c77ae28d4a02f4019a6aa5c`.
This was verified against the actual release APK using build-tools apksigner.
The same private key is required to update those installations. A newly generated debug
keystore is a different key and cannot update them. Do not generate or commit a replacement.
A future production-key migration needs an explicit compatibility plan.

Without signing environment variables, Gradle produces `app-release-unsigned.apk` for
validation only. It cannot be installed or published. For a signed local build, supply all of:
`KH_KEYSTORE_PATH` (absolute path), `KH_STORE_PASSWORD`, `KH_KEY_ALIAS`, `KH_KEY_PASSWORD`.
Run `python3 scripts/verify-release.py <signed-apk>` with `ANDROID_HOME` pointing to the SDK.
It rejects any signing certificate that differs from v3.2. Never post credentials in logs or PRs.

Configure these GitHub Actions secrets securely, using the EXISTING keystore:
- `KH_KEYSTORE_BASE64`: base64-encoded keystore (base64 is encoding, not encryption)
- `KH_STORE_PASSWORD`
- `KH_KEY_ALIAS`
- `KH_KEY_PASSWORD`

Do not upload the keystore as a workflow artifact. The workflow decodes it outside the checkout
with restrictive permissions, removes it after signing, and validates the certificate before upload.
Missing credentials fail the signing job; they never cause fallback to a generated key.

## Workflow

PRs run clean, both variants' unit tests, lint, and an unsigned release build, then upload
validation artifacts and reports. Manual dispatch also signs an APK when secrets are configured;
it does not publish. Tags matching `v<versionName>` run the same checks, sign, and publish an APK,
JSON metadata, and SHA-256 file only after validation. The tag must belong to master history.
Enable GitHub Actions and permit the workflow's contents-write permission for publishing.
Existing releases are never overwritten automatically.

Bump versionCode above every published version and follow the existing versionName convention.
Merge through a PR after checks pass. Add truthful notes at `docs/releases/<versionName>.md`,
then create and push the corresponding version tag. Publication creates a draft with its actual
assets, publishes it, downloads the APK through GitHub CLI and the public URL, and compares bytes.
Metadata records the exact checkout commit, package, version, SDK, certificate, size, and checksum.

Before tagging, run device QA: install over the previous release with `adb install -r`, launch,
check KH branding and absent Home chips, switch every source, search, browse movies/series/anime
and countries, load images/details/episodes, attempt each available player, and check scrolling
and Back. Save relevant logcat output and report upstream failures. No device means these checks
remain unverified; do not claim successful runtime testing.
