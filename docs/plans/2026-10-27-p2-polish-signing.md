# P2 Polish and Release Signing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hilangkan tiga cacat tampilan yang tersisa dan siapkan jalur rilis bertanda tangan tanpa menyimpan rahasia di repo.

**Architecture:** Label tipe soal dipindah ke pemetaan murni (bisa diuji tanpa Compose). Bar bawah ujian dibuat responsif dengan bobot + satu baris teks. Signing release membaca keystore dari properti Gradle/env, gagal jelas bila tidak ada, dan tidak pernah menyimpan kredensial.

**Tech Stack:** Kotlin, Jetpack Compose, Paparazzi (snapshot), Gradle signing config, JUnit4.

**Spec:** `docs/plans/2026-10-27-p2-polish-signing.md` (temuan P2 dari riwayat sesi)

## Global Constraints

- minSdk 24, compileSdk 35, Kotlin 2.1.10, AGP 8.9.
- Tidak ada rahasia di repo: keystore path/password hanya dari `-P`/env.
- Semua kode produksi baru didahului test yang gagal.
- Bahasa UI Indonesia; tidak mengubah alur ujian yang sudah ada.

## Review Focus

- Layar sempit (≤360dp): tombol bar bawah tidak terpotong dan tidak membungkus.
- Tipe soal tak dikenal: label tetap terbaca, bukan teks mentah berbahasa Inggris.
- Build release tanpa kredensial: gagal dengan pesan jelas, bukan APK unsigned diam-diam.
- Snapshot Paparazzi: state baru tidak mengubah golden lama tanpa sengaja.

---

### Task 1: Indonesian question-type label

**Files:**
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/QuestionTypeLabel.kt`
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/ExamScreen.kt` (badge text)
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/QuestionTypeLabelTest.kt`

**Interfaces:**
- Produces: `fun questionTypeLabel(type: String): String` — `multiple_choice`→`PILIHAN GANDA`, `true_false`→`BENAR / SALAH`, `essay`→`URAIAN`, `short_answer`→`JAWABAN SINGKAT`; unknown → uppercase with `_`→space; blank → `SOAL`.

- [ ] **Step 1: Write the failing test**

```kotlin
class QuestionTypeLabelTest {
    @Test fun `maps known types`() {
        assertEquals("PILIHAN GANDA", questionTypeLabel("multiple_choice"))
        assertEquals("BENAR / SALAH", questionTypeLabel("true_false"))
        assertEquals("URAIAN", questionTypeLabel("essay"))
    }
    @Test fun `unknown type falls back to readable uppercase`() {
        assertEquals("MATCHING PAIRS", questionTypeLabel("matching_pairs"))
    }
    @Test fun `blank type returns generic label`() {
        assertEquals("SOAL", questionTypeLabel("   "))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuestionTypeLabelTest'`
Expected: FAIL — unresolved `questionTypeLabel`.

- [ ] **Step 3: Implement the mapping and use it in ExamScreen**

Replace `currentQ.type.replace("_", " ").uppercase()` with `questionTypeLabel(currentQ.type)`.

- [ ] **Step 4: Verify**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuestionTypeLabelTest'` → PASS 3/3, then full `:app:testDebugUnitTest` and `:app:verifyPaparazziDebug` (badge golden changes: re-record only `examScreen` via `recordPaparazziDebug`, inspect the diff).

- [ ] **Step 5: Commit**

`git add -A && git commit -m "feat(ui): localize question type badge"`

---

### Task 2: Responsive exam bottom bar

**Files:**
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/ExamScreen.kt` (bottom bar Row, buttons)
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/ScreenshotTest.kt` (narrow-device snapshot)

**Interfaces:**
- No new public API. Behavior: at 320dp width, all three controls render fully with `maxLines=1`, no ellipsis on the primary action, and equal-height buttons.

- [ ] **Step 1: Add the failing snapshot**

Add a test using `Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 320, screenHeight = 640))` (or a custom `DeviceConfig` with 320dp width) rendering `PreviewExamScreen()`. Record it and inspect: the current implementation clips "Selanjutnya".

- [ ] **Step 2: Verify the failure is real**

Run `./gradlew :app:recordPaparazziDebug`, open `app/src/test/snapshots/images/...narrow...png`, confirm clipping. This is the RED state (visual, not assertion).

- [ ] **Step 3: Make the bar responsive**

Use `Row(horizontalArrangement = Arrangement.spacedBy(8.dp))` with `Modifier.weight(1f)` on "Sebelumnya" and "Ragu-ragu", and `weight(1.2f)` on the primary action; `contentPadding = PaddingValues(horizontal = 8.dp)`, `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis` only on the secondary buttons.

- [ ] **Step 4: Verify**

Run `./gradlew :app:recordPaparazziDebug`, inspect the narrow snapshot (no clipping, "Selanjutnya" complete), then `./gradlew :app:verifyPaparazziDebug` and full `:app:testDebugUnitTest`.

- [ ] **Step 5: Commit**

`git add -A && git commit -m "fix(ui): make exam bottom bar fit narrow screens"`

---

### Task 3: Release signing from external credentials

**Files:**
- Modify: `app/build.gradle.kts` (signingConfigs, buildTypes.release)
- Create: `docs/RELEASING.md`
- Test: `app/src/testRelease/kotlin/id/sch/mts241alikhlash/cbt/ReleaseConfigTest.kt` (extend)

**Interfaces:**
- Signing inputs: `-PRELEASE_STORE_FILE`, `-PKEYSTORE_PASSWORD`, `-PKEY_ALIAS`, `-PKEY_PASSWORD` (or env `RELEASE_STORE_FILE` etc.).
- Behavior: `assembleRelease` without credentials fails with a message naming the missing property; with credentials, produces a signed APK.

- [ ] **Step 1: Write the failing test**

Extend `ReleaseConfigTest` with a check that the release build type is configured for signing only when credentials exist — assert via `BuildConfig`? Signing is not visible in BuildConfig. Instead write a Gradle-level test: a small script assertion is not unit-testable. **Use this step instead:** add `./gradlew :app:signingReport`-based verification documented in RELEASING.md, and a build-time `require` in Gradle. TDD exception: configuration code has no JVM test seam; the check is the Gradle invocation itself (documented as the cheapest failing check per plan rules).

Run first: `./gradlew :app:assembleRelease` (no credentials) → currently succeeds with an unsigned APK. That is the failing state to change.

- [ ] **Step 2: Implement signing config**

In `app/build.gradle.kts`:

```kotlin
val storeFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val hasSigning = storeFile != null
signingConfigs {
    if (hasSigning) create("release") {
        storeFile = file(storeFile!!)
        storePassword = providers.gradleProperty("KEYSTORE_PASSWORD").orNull
        keyAlias = providers.gradleProperty("KEY_ALIAS").orNull
        keyPassword = providers.gradleProperty("KEY_PASSWORD").orNull
    }
}
buildTypes.release {
    signingConfig = signingConfigs.findByName("release")
}
```

Plus a guard that inspects the **resolved** task graph (`gradle.taskGraph.whenReady`) for the tasks that actually emit a release artifact (`packageRelease`, `packageReleaseBundle`, `packageReleaseUniversalApk`, `signReleaseBundle`) and throws when signing is incomplete, with the message: `Release build needs -PRELEASE_STORE_FILE, -PKEYSTORE_PASSWORD, -PKEY_ALIAS, -PKEY_PASSWORD`. Do not key the guard on `gradle.startParameter.taskNames`: Gradle accepts abbreviations (`aR`, `assembleRel`, bare `a`), so a raw-name check misses them and ships an unsigned APK.

- [ ] **Step 3: Verify both paths**

Without credentials: `./gradlew :app:assembleRelease` → FAIL with that message. With a throwaway keystore: `keytool -genkeypair` into `/tmp`, then `./gradlew :app:assembleRelease -PRELEASE_STORE_FILE=/tmp/test.jks -PKEYSTORE_PASSWORD='…' -PKEY_ALIAS=test -PKEY_PASSWORD='…'` → SUCCESS and `apksigner verify --print-certs` shows the cert. Never paste a real or reusable password into a command line or a document; generate a random throwaway one and delete the keystore afterwards.

- [ ] **Step 4: Document**

`docs/RELEASING.md`: keystore creation, property names, CI secret mapping, `verifyPaparazziDebug` + `test` before release, and a warning never to commit the keystore.

- [ ] **Step 5: Commit**

`git add -A && git commit -m "build(release): sign with external credentials and document releasing"`
