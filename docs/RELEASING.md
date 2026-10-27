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

All four are required. The build checks them at configuration time and stops with:

```
Release build needs -PRELEASE_STORE_FILE, -PKEYSTORE_PASSWORD, -PKEY_ALIAS,
-PKEY_PASSWORD (or the same names as environment variables).
```

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
    RELEASE_STORE_FILE: ${{ github.workspace }}/release.jks
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: |
    printf '%s' "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 -d > "$RELEASE_STORE_FILE"
    ./gradlew :app:assembleRelease
    rm -f "$RELEASE_STORE_FILE"
```

The keystore itself is supplied as a base64-encoded secret and written to a
temporary path inside the runner workspace, then deleted. Never echo the passwords;
mask them in CI logs.

## Pre-release checks

Run all of these before tagging a release:

```bash
./gradlew test                    # unit tests
./gradlew :app:verifyPaparazziDebug  # UI snapshot goldens
./gradlew :app:assembleRelease    # signed APK (requires credentials)
```

Then verify the APK signature with `apksigner verify --print-certs` as shown above
and confirm the certificate fingerprint matches the expected release key.

## Never commit the keystore

**Do not commit the `.jks`/`.keystore` file, its passwords, or a `keystore.properties`
file to version control.** This repository ignores build output but does not track
key material; keep it in encrypted storage and CI secrets only. A leaked keystore
plus password lets anyone publish malicious updates under the app's identity.
