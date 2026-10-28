# P3 Account Login and Exam List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Siswa masuk dengan akun platform, melihat daftar ujian miliknya, memasukkan token ujian, lalu mengerjakan; guru hanya dipakai sebagai pengawas untuk membuka kunci kiosk.

**Architecture:** Lapisan `auth` baru (`AuthApiService`, `TokenStore`, `AuthRepository`) di atas Retrofit yang ada; `OkHttpClient` mendapat `AuthInterceptor` dan `TokenAuthenticator`. `ExamRepository` mendapat `listExams()` dan `startSession()`, deadline ditentukan server. `AppRootState` bertambah dua layar (login, daftar ujian) sebelum `TokenScreen`. PIN pengawas statis diganti verifikasi akun guru. Backend (identity native login dan exam-service fase 2) belum ada: kontrak ditulis di Task 1 dan klien diuji dengan fake `MockWebServer`.

**Tech Stack:** Kotlin 2.1.10, Jetpack Compose, Retrofit/OkHttp, kotlinx-serialization, DataStore, Android Keystore (AES/GCM), JUnit4, kotlinx-coroutines-test, okhttp `mockwebserver`.

**Spec:** Keputusan owner 2026-10-28 di sesi ini (opsi A: login dalam app, guru jadi pengawas). Pola kerja mengikuti `docs/plans/2026-10-27-p1-exam-resilience.md`.

## Global Constraints

- minSdk 24, compileSdk 35, JDK 17 target, Kotlin 2.1.10.
- Tidak ada komentar di kode baru. Komentar yang sudah ada tidak dihapus.
- Pesan error berbahasa Indonesia, singkat, tanpa detail internal.
- Semua kode produksi baru didahului test yang gagal (TDD).
- Jangan mengarang endpoint di luar kontrak Task 1; kontrak harus disetujui owner dan tim identity sebelum backend dibuat.
- Respons backend dibungkus envelope `{ "statusCode": Int, "message": String, "data": T }` (`ResponseInterceptor`); error `{ "statusCode", "message", "data": null }`. Klien tidak boleh membaca body mentah.
- Token tidak pernah dicatat ke log; `HttpLoggingInterceptor` memakai `redactHeader("Authorization")`.
- Kunci jawaban tidak pernah ada di DTO klien.
- Commit hanya bila owner meminta; stage per path; tanpa baris atribusi.

## Review Focus

- Refresh token kedaluwarsa saat ujian berjalan → siswa tidak dikeluarkan; jawaban tetap tersimpan lokal.
- Dua request 401 bersamaan → tepat satu panggilan refresh.
- Jam perangkat salah → deadline tetap benar (dihitung dari `server_time`).
- Akun guru mencoba masuk di layar login siswa → ditolak dengan pesan jelas.
- Login berhasil, daftar ujian kosong → tampilan kosong yang bisa dimengerti, bukan layar putih.
- Restart saat ujian berjalan → langsung kembali ke ujian tanpa login ulang.
- Respons error tanpa body atau bukan JSON → pesan umum, tidak crash.

## File Structure

- Create `docs/API-CONTRACT.md` — kontrak yang diminta klien.
- Create `data/remote/ApiEnvelope.kt` — `ApiEnvelope<T>`, `ApiException`, `unwrap()`.
- Create `data/auth/AuthModels.kt`, `AuthApiService.kt`, `TokenStore.kt`, `KeystoreTokenStore.kt`, `AuthRepository.kt`, `AuthInterceptor.kt`, `TokenAuthenticator.kt`, `SupervisorVerifier.kt`.
- Modify `data/remote/ExamApiService.kt`, `data/model/QuestionModels.kt`, `data/repository/ExamRepository.kt`, `data/session/SessionRepository.kt`, `CBTApplication.kt`, `MainActivity.kt`, `ui/AppRoot.kt`, `ui/SupervisorExitDialog.kt`, `app/build.gradle.kts`.
- Create `ui/screens/LoginScreen.kt`, `LoginViewModel.kt`, `ExamListScreen.kt`, `ExamListViewModel.kt`.
- Paths di bawah relatif ke `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/` (test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/`).

---

### Task 1: Contract and envelope

**Files:**
- Create: `docs/API-CONTRACT.md`, `data/remote/ApiEnvelope.kt`
- Test: `data/remote/ApiEnvelopeTest.kt`
- Modify: `app/build.gradle.kts` (add `testImplementation("com.squareup.okhttp3:mockwebserver:<versi okhttp yang dipakai>")`)

**Interfaces:**
- Produces:
  - `@Serializable data class ApiEnvelope<T>(val statusCode: Int, val message: String, val data: T?)`
  - `class ApiException(val statusCode: Int, override val message: String) : Exception(message)`
  - `fun <T> ApiEnvelope<T>.unwrap(): T` — melempar `ApiException` bila `data == null`.
  - `suspend fun <T> apiCall(block: suspend () -> ApiEnvelope<T>): T` — memetakan `HttpException`, `IOException`, dan body non-JSON ke `ApiException` (status 0 untuk jaringan).

- [ ] **Step 1: Tulis `docs/API-CONTRACT.md`** berisi tepat endpoint berikut (semua respons dalam envelope; snake_case):

| Endpoint | Body | `data` |
|---|---|---|
| `POST /auth/native/login` | `{identifier, password, device_id}` | `{access_token, refresh_token, expires_in, user: {id, name, roles[]}}` |
| `POST /auth/native/refresh` | `{refresh_token, device_id}` | sama dengan login (refresh dirotasi) |
| `POST /auth/native/logout` | `{refresh_token}` | `null` |
| `GET /me/exams` | — | `[{id, title, subject_name, starts_at, ends_at, duration_minutes, status}]`, `status` ∈ `UPCOMING`, `OPEN`, `IN_PROGRESS`, `FINISHED`; `starts_at`/`ends_at` ISO-8601 UTC |
| `POST /me/exams/{examId}/sessions` | `{access_code, device_id}` | `{session_id, deadline, server_time}` (epoch millis; 409 bila sesi aktif di perangkat lain; 403 bila token ujian salah) |
| `GET /student-exams/{sessionId}/questions` | — | `[StudentQuestionDto]` (sudah ada) |
| `POST /student-exams/{sessionId}/answers` | `{question_id, answer, client_id}` | `StudentAnswerDto`; upsert per `question_id`, `client_id` UUID untuk idempotensi |
| `POST /student-exams/{sessionId}/submit` | — | `null` |

  Tambahkan catatan: login native hanya menerima role `STUDENT` dan `TEACHER`; `TEACHER` dipakai untuk verifikasi pengawas lewat endpoint login yang sama.

- [ ] **Step 2: Tulis test gagal** di `ApiEnvelopeTest`: `unwrap_returns_data_when_present`, `unwrap_throws_ApiException_with_server_message_when_data_null`, `apiCall_maps_http_401_body_to_ApiException_401` (pakai `MockWebServer` dengan body `{"statusCode":401,"message":"Kredensial salah","data":null}`), `apiCall_maps_non_json_500_to_generic_message`, `apiCall_maps_IOException_to_status_0`.
- [ ] **Step 3: Jalankan** `./gradlew :app:testDebugUnitTest --tests '*ApiEnvelopeTest'`. Expected: FAIL (tipe belum ada).
- [ ] **Step 4: Implementasikan** `ApiEnvelope.kt` sesuai antarmuka di atas. Pesan generik: `"Terjadi kesalahan. Coba lagi."`; jaringan: `"Tidak dapat terhubung ke server."`.
- [ ] **Step 5: Jalankan test yang sama.** Expected: PASS.

---

### Task 2: Token storage and auth repository

**Files:**
- Create: `data/auth/AuthModels.kt`, `AuthApiService.kt`, `TokenStore.kt`, `KeystoreTokenStore.kt`, `AuthRepository.kt`
- Test: `data/auth/AuthRepositoryTest.kt`, `data/auth/KeystoreTokenStoreTest.kt` (Robolectric tidak dipakai; cipher dibungkus `TokenCipher` agar bisa diganti fake)

**Interfaces:**
- Consumes: `apiCall` dari Task 1.
- Produces:
  - `data class AuthUser(val id: String, val name: String, val roles: Set<String>)`; `val AuthUser.isStudent: Boolean`, `val AuthUser.isTeacher: Boolean`.
  - `data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long)`
  - `interface TokenStore { suspend fun load(): StoredAuth?; suspend fun save(auth: StoredAuth); suspend fun clear() }` dengan `data class StoredAuth(val tokens: AuthTokens, val user: AuthUser)`.
  - `interface TokenCipher { fun encrypt(plain: ByteArray): ByteArray; fun decrypt(cipher: ByteArray): ByteArray }`; `KeystoreTokenStore(context, cipher: TokenCipher = AndroidKeystoreCipher())` menyimpan hasil `encrypt` di DataStore.
  - `class AuthRepository(api: AuthApiService, store: TokenStore, deviceId: () -> String, now: () -> Long)` dengan:
    - `suspend fun login(identifier: String, password: String, requiredRole: String): AuthUser` — melempar `ApiException(403, "Akun ini tidak dapat masuk di sini.")` bila `requiredRole` tidak ada di `roles`, tanpa menyimpan token.
    - `suspend fun current(): StoredAuth?`
    - `suspend fun refresh(): AuthTokens?` — satu panggilan sekaligus (`Mutex`); gagal 401/403 menghapus penyimpanan, gagal jaringan mengembalikan `null` tanpa menghapus.
    - `suspend fun logout()` — memanggil `/auth/native/logout` bila bisa, selalu `store.clear()`.

- [ ] **Step 1: Tulis test gagal** (`FakeAuthApi`, `InMemoryTokenStore`): `login_stores_tokens_and_user`, `login_rejects_teacher_when_student_required_and_stores_nothing`, `refresh_rotates_tokens`, `concurrent_refresh_calls_api_once`, `refresh_clears_store_on_401`, `refresh_keeps_store_on_network_error`, `logout_clears_store_even_when_api_fails`. `KeystoreTokenStoreTest`: `roundtrip_returns_saved_auth` dan `stored_bytes_do_not_contain_refresh_token_plaintext` (fake cipher XOR).
- [ ] **Step 2: Jalankan** `./gradlew :app:testDebugUnitTest --tests '*auth*'`. Expected: FAIL.
- [ ] **Step 3: Implementasikan** semua tipe di atas; `AndroidKeystoreCipher` memakai `AndroidKeyStore` AES/GCM/NoPadding 256-bit, IV 12 byte di depan ciphertext.
- [ ] **Step 4: Jalankan test yang sama.** Expected: PASS.

---

### Task 3: HTTP wiring (bearer, refresh on 401)

**Files:**
- Create: `data/auth/AuthInterceptor.kt`, `data/auth/TokenAuthenticator.kt`
- Modify: `CBTApplication.kt` (bangun `AuthRepository`, pasang interceptor dan authenticator, `redactHeader("Authorization")`)
- Test: `data/auth/TokenAuthenticatorTest.kt`

**Interfaces:**
- Consumes: `AuthRepository.current()`, `AuthRepository.refresh()`.
- Produces: `AuthInterceptor(repo)` menambah `Authorization: Bearer <access>` kecuali path `auth/native/*`; `TokenAuthenticator(repo, examActive: () -> Boolean) : Authenticator` mencoba ulang satu kali dengan token baru; mengembalikan `null` bila refresh gagal atau request sudah dicoba ulang (`priorResponse != null`). Saat `examActive()` true dan refresh gagal, penyimpanan tidak dihapus.

- [ ] **Step 1: Tulis test gagal** dengan `MockWebServer`: `adds_bearer_header`, `skips_header_for_auth_native_paths`, `retries_once_with_new_token_after_401`, `gives_up_when_retry_also_401`, `does_not_clear_store_when_exam_active_and_refresh_fails`.
- [ ] **Step 2: Jalankan** `--tests '*TokenAuthenticatorTest'`. Expected: FAIL.
- [ ] **Step 3: Implementasikan** kedua kelas; `examActive` di `CBTApplication` membaca `@Volatile var examActive` yang diset `MainActivity`.
- [ ] **Step 4: Jalankan.** Expected: PASS.

---

### Task 4: Exam list and server-authoritative session start

**Files:**
- Modify: `data/remote/ExamApiService.kt`, `data/model/QuestionModels.kt`, `data/repository/ExamRepository.kt`, `data/session/SessionRepository.kt`
- Test: `data/repository/ExamRepositoryTest.kt` (sambung yang ada), `data/session/SessionRepositoryTest.kt`

**Interfaces:**
- Consumes: `ApiEnvelope`, `apiCall`.
- Produces:
  - DTO `ExamSummaryDto(id, title, @SerialName("subject_name") subjectName, @SerialName("starts_at") startsAt, @SerialName("ends_at") endsAt, @SerialName("duration_minutes") durationMinutes, status: String)`; `StartSessionRequest(@SerialName("access_code") accessCode, @SerialName("device_id") deviceId)`; `StartedSessionDto(@SerialName("session_id") sessionId, deadline: Long, @SerialName("server_time") serverTime: Long)`; `SubmitAnswerRequest` bertambah `@SerialName("client_id") clientId: String`.
  - Semua fungsi `ExamApiService` mengembalikan `ApiEnvelope<…>`; tambah `listExams()`, `startSession(examId, body)`, `submit(sessionId)`.
  - `ExamRepository.listExams(): List<ExamSummaryDto>` dan `ExamRepository.startSession(examId: String, accessCode: String): StartedSession` dengan `data class StartedSession(val sessionId: String, val deadlineLocalMillis: Long)`; `deadlineLocalMillis = deadline - serverTime + now()`.
  - `SessionRepository.start(id: String, deadline: Long)` — menggantikan `start(id)`; sesi yang sama tetap mempertahankan deadline lama (perilaku P1).
  - `ExamRepository.recordAnswer` menghasilkan `clientId` UUID per jawaban baru dan memakai ulang `clientId` saat retry.

- [ ] **Step 1: Tulis test gagal**: `listExams_unwraps_envelope`, `startSession_converts_deadline_using_server_time_offset` (now=1_000, serverTime=5_000, deadline=65_000 → 61_000), `startSession_409_surfaces_server_message`, `sessionRepository_start_keeps_original_deadline_for_same_id`, `recordAnswer_retry_reuses_client_id`, `getQuestions_unwraps_envelope`.
- [ ] **Step 2: Jalankan** `--tests '*ExamRepositoryTest' --tests '*SessionRepositoryTest'`. Expected: FAIL.
- [ ] **Step 3: Implementasikan** perubahan di atas; sesuaikan pemanggil lama (`MainActivity.onStartExam`, `ExamTimer.deadlineFrom` tetap ada dan dipakai hanya untuk fallback uji).
- [ ] **Step 4: Jalankan seluruh unit test** `./gradlew :app:testDebugUnitTest`. Expected: PASS.

---

### Task 5: Login and exam list screens, navigation

**Files:**
- Create: `ui/screens/LoginScreen.kt`, `LoginViewModel.kt`, `ExamListScreen.kt`, `ExamListViewModel.kt`
- Modify: `ui/AppRoot.kt`, `ui/screens/TokenScreen.kt` (menerima judul ujian dan mengembalikan `accessCode`), `MainActivity.kt`
- Test: `ui/screens/LoginViewModelTest.kt`, `ui/screens/ExamListViewModelTest.kt`, `ui/AppRootThemeTest.kt` (perbarui)

**Interfaces:**
- Consumes: `AuthRepository`, `ExamRepository`.
- Produces:
  - `AppRootState` bertambah `user: AuthUser? = null`, `selectedExam: ExamSummaryDto? = null`; urutan layar: splash → restore error → (sesi aktif → ujian) → belum login → `LoginScreen` → `ExamListScreen` → `TokenScreen(exam)` → ujian.
  - `LoginViewModel(auth: AuthRepository)`: `state: StateFlow<LoginUiState(loading, error)>`, `fun submit(identifier, password)` memakai `requiredRole = "STUDENT"`.
  - `ExamListViewModel(repo: ExamRepository)`: `state: StateFlow<ExamListUiState>` dengan `Loading`, `Empty`, `Content(exams)`, `Error(message)`; `fun refresh()`. Hanya ujian berstatus `OPEN` atau `IN_PROGRESS` yang dapat dipilih.
  - Tombol "Keluar" tersedia di daftar ujian dan hanya ada saat tidak ada sesi ujian aktif.
  - `MainActivity.onStartExam` memanggil `repository.startSession(exam.id, accessCode)`, lalu `sessionRepository.start(id, deadline)`, lalu `startKioskMode()` — kiosk tidak lagi aktif sebelum sesi berhasil dibuat.
  - Restore saat start: bila ada sesi tersimpan → langsung ke ujian (tanpa login); `examActive` true; sinkronisasi jawaban tertunda berjalan seperti P1.

- [ ] **Step 1: Tulis test gagal**: `login_success_exposes_user`, `login_with_teacher_account_shows_message_and_no_user`, `login_error_message_from_server_is_shown`, `list_empty_state_when_no_exams`, `list_error_state_on_network_failure_with_retry`, `only_open_or_in_progress_exams_selectable`, `root_shows_login_when_no_user_and_no_session`, `root_shows_exam_content_when_session_restored_without_user`.
- [ ] **Step 2: Jalankan** `--tests '*LoginViewModelTest' --tests '*ExamListViewModelTest' --tests '*AppRootThemeTest'`. Expected: FAIL.
- [ ] **Step 3: Implementasikan** tipe dan layar; pakai komponen tema yang sudah ada (`BrandNavy`, `DMSansFontFamily`, pola `TokenScreen`).
- [ ] **Step 4: Jalankan semua unit test.** Expected: PASS.
- [ ] **Step 5: Jalankan manual** `./gradlew :app:installDebug` terhadap `MockWebServer` lokal atau `API_BASE_URL_DEBUG` dummy; verifikasi: login siswa → daftar → token → ujian → kembali ke daftar setelah submit.

---

### Task 6: Teacher as supervisor

**Files:**
- Create: `data/auth/SupervisorVerifier.kt`
- Modify: `ui/SupervisorExitDialog.kt`, `MainActivity.kt`, `app/build.gradle.kts` (hapus `SUPERVISOR_PIN` dari kedua `buildConfigField`), `docs/KIOSK.md`, `docs/RELEASING.md` (hapus rujukan PIN)
- Test: `data/auth/SupervisorVerifierTest.kt`, uji dialog yang ada diperbarui

**Interfaces:**
- Consumes: `AuthApiService.login` (endpoint yang sama), `ViolationStore.record`.
- Produces:
  - `class SupervisorVerifier(api: AuthApiService, deviceId: () -> String)` dengan `suspend fun verify(identifier: String, password: String): Boolean` — true hanya bila login berhasil dan `roles` memuat `TEACHER`; token hasilnya tidak disimpan dan segera dibuang; bila jaringan mati mengembalikan false (pesan "Pengawas perlu koneksi untuk membuka kunci.").
  - `SupervisorExitDialog(onDismiss, onVerify: suspend (String, String) -> Boolean, onConfirm)` — dua kolom (akun, kata sandi), menggantikan `expectedPin`.
  - Setiap upaya (berhasil atau gagal) memanggil `violations.record("supervisor_exit")` atau `"supervisor_exit_denied"`.

- [ ] **Step 1: Tulis test gagal**: `verify_true_for_teacher_role`, `verify_false_for_student_role`, `verify_false_on_401`, `verify_false_on_network_error`, `verify_does_not_touch_token_store`.
- [ ] **Step 2: Jalankan** `--tests '*SupervisorVerifierTest'`. Expected: FAIL.
- [ ] **Step 3: Implementasikan** verifier dan dialog baru; hapus `BuildConfig.SUPERVISOR_PIN` dari `MainActivity`.
- [ ] **Step 4: Jalankan semua unit test dan** `./gradlew :app:assembleDebug`. Expected: PASS, build sukses.
- [ ] **Step 5: Perbarui** `docs/KIOSK.md` bagian pengawas (akun guru, butuh koneksi).

---

## Di luar rencana ini (backend, perlu persetujuan tim)

- identity-service: `/auth/native/login|refresh|logout` (token di body, tanpa cookie, `device_id` tercatat di `auth_sessions`).
- exam-service fase 2: `Exam`, jadwal/ruang, `ExamSession`, `ExamAnswer`, endpoint pada kontrak Task 1, `platform/student-lookup` untuk kelas siswa.
- Unggah pelanggaran kiosk ke server dan audit log.

## Self-Review

- Spec: login dalam app (T2, T5), daftar ujian (T4, T5), token ujian (T5), guru sebagai pengawas (T6), deadline dari server (T4), envelope (T1).
- Tipe: `AuthRepository.refresh(): AuthTokens?`, `StartedSession.deadlineLocalMillis`, `SessionRepository.start(id, deadline)` konsisten antar task.
- Review Focus: refresh kedaluwarsa saat ujian (T3), refresh serentak (T2), jam salah (T4), akun guru di layar siswa (T2, T5), daftar kosong (T5), restart saat ujian (T5), error non-JSON (T1).
