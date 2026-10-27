# P1 Exam Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ujian bertahan terhadap restart aplikasi dan jaringan putus: sesi tersimpan, soal ter-cache, jawaban tertunda disinkronkan ulang, dan pengumpulan tidak menandai selesai saat masih ada jawaban gagal kirim.

**Architecture:** Lapisan data diberi antarmuka kecil (`SessionStore`, `QuestionDao`) agar logika bisa diuji tanpa Android. Room menyimpan cache soal (migrasi v1→v2). Repository mengembalikan `SyncReport`; ViewModel menolak menandai selesai bila masih ada kegagalan. MainActivity memulihkan sesi saat start.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore Preferences, Retrofit/OkHttp, kotlinx-coroutines-test, JUnit4.

**Spec:** `docs/plans/2026-10-27-p1-exam-resilience.md` (tidak ada design doc terpisah; temuan P1 ada di riwayat sesi)

## Global Constraints

- minSdk 24, compileSdk 35, JDK 17 target, Kotlin 2.1.10.
- Tidak menambah WorkManager; sinkronisasi dipicu saat start aplikasi dan saat submit.
- Backend belum punya endpoint submit ujian: klien hanya menyelesaikan antrean jawaban + status lokal; jangan mengarang endpoint.
- Semua kode produksi baru harus didahului test yang gagal (TDD).
- Pesan error berbahasa Indonesia, singkat, tanpa detail internal.

## Review Focus

- Start aplikasi saat offline dan cache soal kosong → tampilkan error yang bisa dimengerti, bukan layar kosong.
- Restart di tengah ujian → sesi dan jawaban lokal kembali, timer tidak mengulang dari awal tanpa penanda.
- Submit saat masih ada jawaban gagal → jangan tandai selesai; tampilkan jumlah yang belum terkirim.
- Jawaban ganda untuk soal yang sama → tidak menduplikasi baris cache.
- Cache soal untuk sesi berbeda → tidak tercampur.

---

### Task 1: Session persistence

**Files:**
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/session/SessionStore.kt`
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/session/DataStoreSessionStore.kt`
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/session/SessionRepository.kt`
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/data/session/SessionRepositoryTest.kt`
- Modify: `app/build.gradle.kts` (add `androidx.datastore:datastore-preferences`, `kotlinx-coroutines-test`)

**Interfaces:**
- Produces: `interface SessionStore { suspend fun load(): String?; suspend fun save(id: String); suspend fun clear() }`
- Produces: `class SessionRepository(private val store: SessionStore) { suspend fun current(): String?; suspend fun start(id: String); suspend fun finish() }`

- [ ] **Step 1: Write the failing test**

```kotlin
class SessionRepositoryTest {
    @Test fun `start then current returns id`() = runTest {
        val repo = SessionRepository(InMemorySessionStore())
        repo.start("sess-1")
        assertEquals("sess-1", repo.current())
    }
    @Test fun `finish clears session`() = runTest {
        val repo = SessionRepository(InMemorySessionStore())
        repo.start("sess-1"); repo.finish()
        assertNull(repo.current())
    }
    @Test fun `blank id is rejected`() = runTest {
        val repo = SessionRepository(InMemorySessionStore())
        assertFailsWith<IllegalArgumentException> { repo.start("  ") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SessionRepositoryTest'`
Expected: FAIL — unresolved reference `SessionRepository`.

- [ ] **Step 3: Implement store + repository**

`SessionStore` as above; `SessionRepository.start` requires `id.trim().isNotEmpty()`, saves trimmed id; `current()` delegates; `finish()` calls `clear()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*SessionRepositoryTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Add DataStore adapter and wire MainActivity restore**

`DataStoreSessionStore(context)` uses `preferencesDataStore("exam_session")`, key `session_id`. In `MainActivity.onCreate`, launch a coroutine to read `sessionRepository.current()` and set initial `currentSessionId`; on finish call `sessionRepository.finish()`.

- [ ] **Step 6: Build and commit**

Run: `./gradlew :app:assembleDebug`
Then: `git add -A && git commit -m "feat(session): persist exam session across restarts"`

---

### Task 2: Cache questions in Room (v1 → v2)

**Review:** per-task — data migration / persisted format

**Files:**
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/local/CachedQuestionEntity.kt`
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/local/QuestionDao.kt`
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/local/ExamDatabase.kt` (version 2, add entity + migration)
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/repository/ExamRepository.kt`
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/data/repository/QuestionCacheTest.kt`

**Interfaces:**
- Produces: `@Entity(tableName="cached_questions", primaryKeys=["sessionId","id"]) data class CachedQuestionEntity(sessionId: String, id: String, number: Int, type: String, bodyHtml: String, choicesJson: String?, cachedAt: Long)`
- Produces: `interface QuestionDao { suspend fun replaceForSession(sessionId: String, items: List<CachedQuestionEntity>); suspend fun getForSession(sessionId: String): List<CachedQuestionEntity> }`
- Produces: `ExamRepository.getQuestions(sessionId, forceRefresh: Boolean = false): Result<List<StudentQuestionDto>>` — cache-first, network on empty/force.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `falls back to cache when network fails`() = runTest {
    val api = FakeApi(failure = IOException("offline"))
    val dao = FakeQuestionDao(preloaded = listOf(cached("q1")))
    val repo = ExamRepository(api, FakeAnswerDao(), dao)
    val result = repo.getQuestions("s1")
    assertEquals(listOf("q1"), result.getOrThrow().map { it.id })
}
@Test fun `network success replaces cache`() = runTest {
    val api = FakeApi(questions = listOf(dto("q9")))
    val dao = FakeQuestionDao()
    ExamRepository(api, FakeAnswerDao(), dao).getQuestions("s1")
    assertEquals(listOf("q9"), dao.saved.map { it.id })
}
@Test fun `empty cache and network failure returns failure`() = runTest {
    val repo = ExamRepository(FakeApi(failure = IOException()), FakeAnswerDao(), FakeQuestionDao())
    assertTrue(repo.getQuestions("s1").isFailure)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuestionCacheTest'`
Expected: FAIL — constructor/getQuestions signature missing.

- [ ] **Step 3: Implement entity, DAO, migration, repository cache-first**

Room `@Database(version = 2)`; `Migration(1,2)` creates `cached_questions` table. Repository maps DTO↔entity using `Json.encodeToString(ListSerializer(String.serializer()))` for `choices`. Cache read when `!forceRefresh` and cache non-empty; on network success replace cache; on failure return cached list if any, else failure with message "Tidak dapat memuat soal. Periksa koneksi.".

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuestionCacheTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

`git add -A && git commit -m "feat(cache): cache exam questions with Room v2 migration"`

---

### Task 3: Sync reporting and retry

**Files:**
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/repository/ExamRepository.kt`
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/data/repository/SyncPendingTest.kt`

**Interfaces:**
- Produces: `data class SyncReport(val synced: Int, val failed: Int)`
- Produces: `suspend fun ExamRepository.syncPending(sessionId: String): SyncReport`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `counts synced and failed answers`() = runTest {
    val dao = FakeAnswerDao(unsynced = listOf(ans("q1"), ans("q2")))
    val api = FakeApi(failFor = setOf("q2"))
    val report = ExamRepository(api, dao, FakeQuestionDao()).syncPending("s1")
    assertEquals(1, report.synced); assertEquals(1, report.failed)
}
@Test fun `marks only successful answers synced`() = runTest {
    val dao = FakeAnswerDao(unsynced = listOf(ans("q1"), ans("q2")))
    val api = FakeApi(failFor = setOf("q2"))
    ExamRepository(api, dao, FakeQuestionDao()).syncPending("s1")
    assertEquals(setOf("q1"), dao.markedSynced)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SyncPendingTest'`
Expected: FAIL — `syncPending` returns Unit.

- [ ] **Step 3: Implement SyncReport**

Replace `Unit` return with `SyncReport`, counting each answer; keep `runCatching` per item.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*SyncPendingTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

`git add -A && git commit -m "feat(sync): report sync results instead of swallowing failures"`

---

### Task 4: Submit only when sync succeeds

**Files:**
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/ExamViewModel.kt`
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/ExamScreen.kt` (show `submitError` in dialog)
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/ExamViewModelSubmitTest.kt`

**Interfaces:**
- Produces: `ExamUiState.submitError: String? = null`
- Produces: `ExamViewModel.submitExam()` sets `isSubmitted=true` only when `SyncReport.failed == 0`; otherwise sets `submitError="N jawaban belum terkirim. Coba lagi."`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `submit blocked when answers still pending`() = runTest {
    val vm = ExamViewModel(repoWithPending(1), "s1")
    vm.submitExam(); advanceUntilIdle()
    assertFalse(vm.uiState.value.isSubmitted)
    assertEquals("1 jawaban belum terkirim. Coba lagi.", vm.uiState.value.submitError)
}
@Test fun `submit succeeds when all synced`() = runTest {
    val vm = ExamViewModel(repoWithPending(0), "s1")
    vm.submitExam(); advanceUntilIdle()
    assertTrue(vm.uiState.value.isSubmitted)
    assertNull(vm.uiState.value.submitError)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExamViewModelSubmitTest'`
Expected: FAIL — `submitError` missing.

- [ ] **Step 3: Implement state + gating**

Add `submitError` to `ExamUiState`; in `submitExam`, call `syncPending`, branch on `failed`; clear error at start. Dialog shows error text when non-null.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExamViewModelSubmitTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Full suite + commit**

Run: `./gradlew test assembleDebug`
Then: `git add -A && git commit -m "feat(exam): block submission while answers are unsynced"`
