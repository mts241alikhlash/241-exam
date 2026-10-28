# Releasing the app

Release builds are signed with a keystore that lives **outside** this repository.
Gradle reads the credentials from `-P` properties or the matching environment
variables. If they are missing, `assembleRelease` fails immediately with a message
naming every required input instead of silently producing an unsigned APK.

## Required inputs

| Gradle property       | Environment variable | Meaning                                  |
| --------------------- | -------------------- | ---------------------------------------- |
| `RELEASE_STORE_FILE`  | `RELEASE_STORE_FILE` | Absolute path to the `.jks` keystore     |
| `KEYSTORE_PASSWORD`   | `KEYSTORE_PASSWORD`  | Keystore (store) password                |
| `KEY_ALIAS`           | `KEY_ALIAS`          | Key alias inside the keystore            |
| `KEY_PASSWORD`        | `KEY_PASSWORD`       | Password for that key                    |

All four are required. The build validates them before any task runs — at configuration
time when a packaging task was named literally, and against the resolved task graph
otherwise — and stops with:

```
Release build needs -PRELEASE_STORE_FILE, -PKEYSTORE_PASSWORD, -PKEY_ALIAS, -PKEY_PASSWORD (or the same names as environment variables). Missing: RELEASE_STORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. See docs/RELEASING.md.
```

The `Missing:` list reflects what is actually absent, so a partial set only names the
unset values. If `RELEASE_STORE_FILE` points at a path that does not exist, the list
shows `RELEASE_STORE_FILE (file not found: /path/to/keystore.jks)` instead of failing
later with a generic packaging error.

The check runs against the **resolved task graph**, so abbreviated invocations
(`./gradlew aR`, `assembleRel`, or a bare `a`) that expand to a release packaging task
fail exactly like the full name. It keys on the tasks that actually produce the
artifact — `packageRelease`, `packageReleaseBundle`, `packageReleaseUniversalApk`,
`signReleaseBundle` — so non-packaging release tasks such as
`:app:packageReleaseResources`, `:app:bundleReleaseResources`,
`:app:signingConfigWriterRelease` and `:app:testReleaseUnitTest` still run without
credentials.

## Creating the keystore (once, kept offline)

```bash
keytool -genkeypair \
  -v \
  -keystore ~/keys/exam-cbt-release.jks \
  -alias exam-cbt \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Exam CBT, OU=Release, O=Sekolah, L=Jakarta, S=DKI, C=ID"
```

`keytool` prompts for the store and key passwords. Use the **same strong password**
for both, or record them in your password manager — losing them means you can no
longer publish updates for the same application ID.

Back the keystore up somewhere safe and private (encrypted storage, secrets
manager). It is the only way to sign updates to an already-published app.

## Building a signed release locally

```bash
export RELEASE_STORE_FILE="$HOME/keys/exam-cbt-release.jks"
export KEYSTORE_PASSWORD='…'
export KEY_ALIAS='exam-cbt'
export KEY_PASSWORD='…'

./gradlew :app:assembleRelease
```

Or pass properties explicitly:

```bash
./gradlew :app:assembleRelease \
  -PRELEASE_STORE_FILE="$HOME/keys/exam-cbt-release.jks" \
  -PKEYSTORE_PASSWORD='…' \
  -PKEY_ALIAS='exam-cbt' \
  -PKEY_PASSWORD='…'
```

The signed artifact lands at `app/build/outputs/apk/release/app-release.apk`.
Verify it before distributing:

```bash
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Do **not** put passwords on shared shells or in shell history if you can avoid it;
prefer environment variables exported from a private session or a secrets manager.

## CI secret mapping

Store the four values as encrypted CI secrets and expose them to the release job as
environment variables with the exact names above. Example for GitHub Actions:

```yaml
- name: Build signed release
  env:
    RELEASE_STORE_FILE: ${{ runner.temp }}/release.jks
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
    RELEASE_KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }}
  run: |
    trap 'rm -f "$RELEASE_STORE_FILE"' EXIT
    printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 -d > "$RELEASE_STORE_FILE"
    ./gradlew :app:assembleRelease
```

The keystore is decoded into `$RUNNER_TEMP` (outside the checkout) and removed by
the `EXIT` trap even if the build fails. The base64 blob is passed through `env:`
rather than interpolated into the shell script, so it never appears in the rendered
command. Never echo the passwords; mask them in CI logs.

## Pre-release checks

Run all of these before tagging a release:

```bash
./gradlew test                    # unit tests
./gradlew :app:verifyPaparazziDebug  # UI snapshot goldens
./gradlew :app:assembleRelease    # signed APK (requires credentials)
```

You can also confirm Gradle picked up the signing config before packaging:

```bash
./gradlew :app:signingReport
```

`signingReport` is a **report**, not a gate: it exits 0 even when no credentials were
read. Read the `Variant: release` block, not the exit code. With the four inputs set it
must look like this (fingerprints abbreviated; passwords are never printed):

```
Variant: release
Config: release
Store: /home/you/keys/exam-cbt-release.jks
Alias: exam-cbt
MD5: 48:14:1C:7F:…
SHA1: 33:3D:5D:59:…
SHA-256: 4B:69:D8:F5:…
Valid until: Friday, October 30, 2026
----------
```

Without credentials the same block reads:

```
Variant: release
Config: null
Store: null
Alias: null
----------
```

**`Config: null` means the release build type has no signing config**, i.e. the
credentials were not read — the APK would be unsigned. A non-null `Config` is not
sufficient on its own either: `Store` and `Alias` must both be non-null, and the block
must show certificate digests rather than an `Error: Failed to read key …` line (that
line means the store or key password is wrong, and `signingReport` still exits 0).

So the check is: `Config: release`, non-null `Store` and `Alias`, and a digest present.
Then verify the APK signature with `apksigner verify --print-certs` as shown above and
confirm the certificate fingerprint matches the expected release key.

## Never commit the keystore

**Do not commit the `.jks`/`.keystore` file, its passwords, or a `keystore.properties`
file to version control.** This repository ignores build output but does not track
key material; keep it in encrypted storage and CI secrets only. A leaked keystore
plus password lets anyone publish malicious updates under the app's identity.
