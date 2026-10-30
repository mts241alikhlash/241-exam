# Exam Camera Proctoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stream a student's front camera to a supervisor page while an exam session is `IN_PROGRESS`, in a `proctor` build flavor only, without ever blocking answering.

**Architecture:** The Android app publishes WHIP (WebRTC over HTTPS) from a foreground service; MediaMTX republishes HLS; exam-service issues short-lived publish tokens, records camera failures, and lists per-session stream URLs; `assessment-web` renders the grid.

**Tech Stack:** Kotlin/Compose (minSdk 24, targetSdk 35), RootEncoder 2.x, NestJS + Prisma/Postgres, MediaMTX, Vue 3 + hls.js.

**Spec:** `docs/superpowers/specs/2026-10-30-exam-camera-proctoring-design.md`

## Global Constraints

- Trial runs on students' own phones: **no Device Owner**, no boot receiver, no `RECEIVE_BOOT_COMPLETED`.
- Camera runs only while a session is `IN_PROGRESS`; it stops on submit, supervisor release, and `EXPIRED`.
- A camera failure must never block answering, syncing, or submitting.
- `standard` flavor must contain no `CAMERA` permission and no `ProctorService` class.
- `applicationIdSuffix` is empty on both flavors.
- Streams are live only. MediaMTX recording stays off; no record endpoint is added.
- Publish path is HTTPS/WHIP on 443. No RTMP.
- `camera_blocked` is **not** counted by `ViolationPolicy.COUNTED`.
- Answers keep saving locally when the stream is down (existing Room queue, untouched).

## Review Focus

- Student presses Home mid-exam: stream must continue (camera FGS already running), not die.
- Student stops the app from the FGS notification: seat must go red within 60 s, not stay green.
- Session submitted on another device or expired server-side: this device must not keep streaming.
- Publish token reuse after the session ends: must be rejected, not accepted until expiry.
- Teacher without `exam-sessions.read` hitting the streams endpoint: must get 403, not data.

---

### Task 1: Build flavor split

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/proctor/AndroidManifest.xml`
- Create: `app/src/proctor/kotlin/id/sch/mts241alikhlash/cbt/proctor/ProctorFlags.kt`
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/proctor/ProctorFlagsTest.kt`

**Interfaces:**
- Produces: `object ProctorFlags { const val ENABLED: Boolean }` — `false` in `standard`, `true` in `proctor`. Later tasks read `ProctorFlags.ENABLED` from shared code and never import camera classes.

- [ ] **Step 1: Add the flavor dimension and both flavors**

In `app/build.gradle.kts`, inside `android { }`, after `defaultConfig`:

```kotlin
flavorDimensions += "proctoring"
productFlavors {
    create("standard") {
        dimension = "proctoring"
        buildConfigField("boolean", "PROCTORING_ENABLED", "false")
    }
    create("proctor") {
        dimension = "proctoring"
        buildConfigField("boolean", "PROCTORING_ENABLED", "true")
    }
}
```

- [ ] **Step 2: Write the failing test**

`app/src/testStandard/kotlin/id/sch/mts241alikhlash/cbt/proctor/ProctorFlagsTest.kt`:

```kotlin
package id.sch.mts241alikhlash.cbt.proctor

import org.junit.Assert.assertFalse
import org.junit.Test

class ProctorFlagsTest {
    @Test
    fun `standard flavor has proctoring disabled`() {
        assertFalse(ProctorFlags.ENABLED)
    }
}
```

- [ ] **Step 3: Run it to confirm it fails**

Run: `./gradlew testStandardDebugUnitTest --tests '*ProctorFlagsTest*'`
Expected: FAIL — unresolved reference `ProctorFlags`.

- [ ] **Step 4: Create the flag object**

`app/src/main/kotlin/id/sch/mts241alikhlash/cbt/proctor/ProctorFlags.kt`:

```kotlin
package id.sch.mts241alikhlash.cbt.proctor

object ProctorFlags {
    val ENABLED: Boolean = BuildConfig.PROCTORING_ENABLED
}
```

One shared file is enough: the flavor `buildConfigField` already makes `BuildConfig.PROCTORING_ENABLED`
differ per flavor, so no second copy is needed. Also create `app/src/proctor/AndroidManifest.xml`
with an empty `<manifest xmlns:android="http://schemas.android.com/apk/res/android" />` root so the
source set exists for later tasks.

- [ ] **Step 5: Run each flavor's test in its own source set**

Put the assertion in flavor test source sets, not the shared one:
`app/src/testStandard/kotlin/.../ProctorFlagsTest.kt` asserts `assertFalse(ProctorFlags.ENABLED)`,
`app/src/testProctor/kotlin/.../ProctorFlagsTest.kt` asserts `assertTrue(ProctorFlags.ENABLED)`.

Run: `./gradlew testStandardDebugUnitTest testProctorDebugUnitTest --tests '*ProctorFlagsTest*'`
Expected: PASS in both.

- [ ] **Step 6: Prove `standard` has no camera surface**

Run: `./gradlew assembleStandardDebug && aapt dump permissions app/build/outputs/apk/standard/debug/app-standard-debug.apk | grep -i camera`
Expected: no output, exit code 1 from grep.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/id/sch/mts241alikhlash/cbt/proctor app/src/proctor app/src/testStandard app/src/testProctor
git commit -m "build: add proctoring flavor dimension"
```

---

### Task 2: Publish token and camera-blocked endpoints

**Review:** per-task — authentication and persisted format

**Files:**
- Modify: `services/exam-service/prisma/exam-session.prisma`
- Create: `services/exam-service/src/exam-session/application/use-cases/issue-stream-token/issue-stream-token.use-case.ts`
- Create: `services/exam-service/src/exam-session/application/use-cases/report-camera-blocked/report-camera-blocked.use-case.ts`
- Create: `services/exam-service/src/exam-session/presentation/http/dto/stream-token.dto.ts`
- Modify: `services/exam-service/src/exam-session/presentation/http/session-exam.controller.ts`
- Modify: `services/exam-service/src/exam-session/domain/entities/exam-session.entity.ts`
- Modify: `services/exam-service/src/exam-session/domain/repositories/exam-session.repository.ts`
- Modify: `services/exam-service/src/exam-session/infrastructure/persistence/prisma/prisma-exam-session.mapper.ts`
- Modify: `services/exam-service/src/exam-session/infrastructure/persistence/prisma/prisma-exam-session.repository.ts`
- Modify: `services/exam-service/src/exam-session/exam-session.module.ts`
- Test: `services/exam-service/src/exam-session/application/use-cases/issue-stream-token/issue-stream-token.use-case.spec.ts`

**Interfaces:**
- Produces: `IssueStreamTokenUseCase.execute(actor: SessionActor, sessionId: string, now: Date): Promise<{ publishUrl: string; expiresAt: Date }>`; `ReportCameraBlockedUseCase.execute(actor: SessionActor, sessionId: string, reason: string, now: Date): Promise<void>`; `ExamSession.streamNonce: string | null`, `ExamSession.lastSeenAt: Date | null`, `ExamSession.cameraBlocked: string | null`; repository `touchStream(id, nonce, at): Promise<void>` and `setCameraBlocked(id, reason): Promise<void>`.
- Consumes: `SessionAccessService.loadOwned(actor, sessionId, now)` — `actor` is `{ id, deviceId }` and the service already rejects a foreign device with 409 and finalizes a due session.

- [ ] **Step 1: Add the schema fields**

In `prisma/exam-session.prisma`, inside `model ExamSession`, after `violationCount`:

```prisma
  /// Nonce of the last publish token issued; rotating it invalidates older tokens.
  streamNonce     String?   @map("stream_nonce") @db.VarChar(64)
  /// Written by the app's heartbeat; a stale value is what turns a seat red.
  lastSeenAt      DateTime? @map("last_seen_at")
  /// Why the camera is not streaming: network, permission, battery, stopped.
  cameraBlocked   String?   @map("camera_blocked") @db.VarChar(32)
```

Run: `cd services/exam-service && npx prisma migrate dev --name add_stream_tracking`
Expected: a new migration directory and a regenerated client.

- [ ] **Step 2: Write the failing test**

`issue-stream-token.use-case.spec.ts` — three cases: a session not `IN_PROGRESS` rejects; a second call rotates the nonce; the returned `expiresAt` equals `min(session.deadline, now + 5 min)`.

```ts
const actor = { id: 'user-1', deviceId: 'dev-1' }

it('refuses a session that is not in progress', async () => {
  const sessions = fakeSessions({ status: 'SUBMITTED' })
  const useCase = new IssueStreamTokenUseCase(accessWith(sessions), sessions, signer)
  await expect(useCase.execute(actor, 'sid', now)).rejects.toThrow()
})

it('rotates the nonce on every call', async () => {
  const sessions = fakeSessions({ status: 'IN_PROGRESS' })
  const useCase = new IssueStreamTokenUseCase(accessWith(sessions), sessions, signer)
  await useCase.execute(actor, 'sid', now)
  const first = sessions.row.streamNonce
  await useCase.execute(actor, 'sid', now)
  expect(sessions.row.streamNonce).not.toEqual(first)
})

it('expires at the earlier of the deadline and five minutes', async () => {
  const sessions = fakeSessions({ status: 'IN_PROGRESS', deadline: new Date(now.getTime() + 60_000) })
  const result = await new IssueStreamTokenUseCase(accessWith(sessions), sessions, signer).execute(actor, 'sid', now)
  expect(result.expiresAt).toEqual(new Date(now.getTime() + 60_000))
})
```

And in `check-stream-token.use-case.spec.ts`, the revocation cases:

```ts
it('rejects a token after the session was submitted', async () => {
  const sessions = fakeSessions({ status: 'SUBMITTED', streamNonce: 'n1' })
  const token = signer.sign({ sub: 'sid', nonce: 'n1', kind: 'whip-publish' })
  expect(await new CheckStreamTokenUseCase(sessions, verifier).execute(token, 'sid')).toBe(false)
})

it('rejects a token whose nonce was rotated away', async () => {
  const sessions = fakeSessions({ status: 'IN_PROGRESS', streamNonce: 'n2' })
  const token = signer.sign({ sub: 'sid', nonce: 'n1', kind: 'whip-publish' })
  expect(await new CheckStreamTokenUseCase(sessions, verifier).execute(token, 'sid')).toBe(false)
})

it('rejects a token used on a different path', async () => {
  const sessions = fakeSessions({ status: 'IN_PROGRESS', streamNonce: 'n1' })
  const token = signer.sign({ sub: 'sid', nonce: 'n1', kind: 'whip-publish' })
  expect(await new CheckStreamTokenUseCase(sessions, verifier).execute(token, 'other')).toBe(false)
})

it('accepts a live token and records the heartbeat', async () => {
  const sessions = fakeSessions({ status: 'IN_PROGRESS', streamNonce: 'n1' })
  const token = signer.sign({ sub: 'sid', nonce: 'n1', kind: 'whip-publish' })
  expect(await new CheckStreamTokenUseCase(sessions, verifier).execute(token, 'sid')).toBe(true)
  expect(sessions.row.lastSeenAt).not.toBeNull()
})
```

- [ ] **Step 3: Run them to confirm they fail**

Run: `cd services/exam-service && npx jest issue-stream-token check-stream-token --silent`
Expected: FAIL — cannot find module.

- [ ] **Step 4: Implement the three use cases and the DTO**

`IssueStreamTokenUseCase` loads the session through `SessionAccessService.loadOwned`, throws a `ConflictException` when `status !== 'IN_PROGRESS'`, generates a nonce (`crypto.randomUUID()`), calls `sessions.touchStream(id, nonce, now)`, and signs a JWT with claims `{ sub: sessionId, nonce, kind: 'whip-publish' }` using `JWT_SECRET`, expiring at `min(session.deadline, now + 5 min)`. `publishUrl` is `${STREAM_PUBLIC_URL}/whip/${sessionId}?token=${jwt}`.

`CheckStreamTokenUseCase` is specified in Step 6.

`ReportCameraBlockedUseCase` writes `cameraBlocked` when it is currently null, so repeated reports do not churn the row, and returns void. It must not touch `violationCount`.

`stream-token.dto.ts` exposes `{ publishUrl, expiresAt, heartbeatSeconds: 20 }` and `{ reason }` (enum: `network`, `permission`, `battery`, `stopped`).

- [ ] **Step 5: Wire the controller routes**

In `SessionExamController`, both under `@RequirePermissions('exam-sessions.take')` and behind the existing `X-Device-Id` header:

```ts
@Post('stream-token')
async streamToken(
  @CurrentUser() user: { id: string },
  @DeviceId() deviceId: string,
  @Param('sessionId', ParseUUIDPipe) sessionId: string,
): Promise<StreamTokenDto> {
  return StreamTokenDto.from(await this.issueStreamToken.execute({ id: user.id, deviceId }, sessionId, new Date()))
}

@Post('camera-blocked')
@HttpCode(204)
async cameraBlocked(
  @CurrentUser() user: { id: string },
  @DeviceId() deviceId: string,
  @Param('sessionId', ParseUUIDPipe) sessionId: string,
  @Body() dto: CameraBlockedDto,
): Promise<void> {
  await this.reportCameraBlocked.execute({ id: user.id, deviceId }, sessionId, dto.reason, new Date())
}
```

The `@DeviceId()` decorator is already used by this controller; it is what makes `loadOwned` reject a foreign device with 409 rather than handing out a token.

- [ ] **Step 6: Add the MediaMTX auth check endpoint**

MediaMTX calls this on every publish attempt. It is a machine-to-machine route, guarded by
`PROVISIONING_SERVICE_TOKEN` (the shared service token already in use in this repo), not by a user JWT.

```ts
@Controller('internal/stream-auth')
export class StreamAuthController {
  @Post()
  async check(@Body() dto: StreamAuthDto): Promise<{ ok: boolean }> {
    return { ok: await this.checkStreamToken.execute(dto.token, dto.path) }
  }
}
```

`CheckStreamTokenUseCase` verifies the JWT signature, requires `kind === 'whip-publish'`, requires
`dto.path` to equal the token's `sub`, and then loads the session: accept only when
`status === 'IN_PROGRESS'` and `streamNonce === token.nonce`. Any other case returns `ok: false`.
It also writes `lastSeenAt = now` on success, so the heartbeat and the publish check are the same
write path.

Register all three use cases (`IssueStreamToken`, `ReportCameraBlocked`, `CheckStreamToken`) in
`exam-session.module.ts` providers, and `StreamAuthController` in controllers.

- [ ] **Step 7: Run the tests**

Run: `cd services/exam-service && npx jest issue-stream-token report-camera-blocked check-stream-token --silent`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add services/exam-service/prisma services/exam-service/src/exam-session
git commit -m "feat(exam): issue stream tokens and record camera failures"
```

---

### Task 3: Supervisor streams endpoint

**Review:** per-task — authorization

**Files:**
- Create: `services/exam-service/src/exam-session/application/use-cases/list-schedule-streams/list-schedule-streams.use-case.ts`
- Create: `services/exam-service/src/exam-session/presentation/http/dto/schedule-stream.dto.ts`
- Modify: `services/exam-service/src/exam-session/presentation/http/exam-session-admin.controller.ts`
- Modify: `services/exam-service/src/exam-session/exam-session.module.ts`
- Test: `services/exam-service/src/exam-session/presentation/http/exam-session-admin.controller.spec.ts`

**Interfaces:**
- Produces: `GET /exam-schedules/:id/streams` → `{ data: [{ sessionId, studentId, studentName, studentNis, status, playbackUrl, lastSeenAt, cameraBlocked, violationCount }], total }`.
- Consumes: `ListScheduleSessionsUseCase` (exists) and the new columns from Task 2.

- [ ] **Step 1: Extend the permission spec first**

Add to the existing route-permission table in `exam-session-admin.controller.spec.ts`:

```ts
streams: ['exam-sessions.read'],
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd services/exam-service && npx jest exam-session-admin.controller --silent`
Expected: FAIL — `streams` route missing.

- [ ] **Step 3: Implement the use case and DTO**

Reuse `ListScheduleSessionsUseCase` for the rows rather than writing a new query. Map each row to `playbackUrl = ${STREAM_PUBLIC_URL}/hls/${sessionId}/index.m3u8` when `status === 'IN_PROGRESS'` and `lastSeenAt` is within 60 s, otherwise `null`. No token is needed for playback: HLS paths are readable only from the gateway, which requires the teacher's session cookie.

- [ ] **Step 4: Add the route**

In `ExamSessionAdminController`, matching the existing style:

```ts
@Get('exam-schedules/:id/streams')
@RequirePermissions('exam-sessions.read')
async streams(@Param('id', ParseUUIDPipe) id: string): Promise<ScheduleStreamListResponseDto>
```

- [ ] **Step 5: Write the authorization test**

```ts
it('rejects a caller without exam-sessions.read', async () => {
  await expect(request(app).get(`/exam-schedules/${id}/streams`)).resolves.toMatchObject({ status: 403 })
})
```

- [ ] **Step 6: Run the tests**

Run: `cd services/exam-service && npx jest exam-session-admin.controller list-schedule-streams --silent`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/exam-service/src/exam-session
git commit -m "feat(exam): expose per-session stream status to supervisors"
```

---

### Task 4: ProctorService publishing WHIP

**Files:**
- Modify: `app/src/proctor/AndroidManifest.xml`
- Create: `app/src/proctor/kotlin/id/sch/mts241alikhlash/cbt/proctor/ProctorService.kt`
- Create: `app/src/proctor/kotlin/id/sch/mts241alikhlash/cbt/proctor/StreamClient.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/testProctor/kotlin/id/sch/mts241alikhlash/cbt/proctor/StreamRetryTest.kt`

**Interfaces:**
- Produces: `ProctorService.start(context, sessionId: String)` and `ProctorService.stop(context)`; `StreamClient.publish(url: String, onEnded: (reason: String) -> Unit)`.
- Consumes: `ProctorFlags.ENABLED` (Task 1); the `stream-token` endpoint (Task 2).

- [ ] **Step 1: Add RootEncoder**

In `gradle/libs.versions.toml` add `rootencoder = "2.5.9"` and
`rootencoder-whip = { group = "com.github.pedroSG94.RootEncoder", name = "library", version.ref = "rootencoder" }`.
In `app/build.gradle.kts` add it under `dependencies` with `"proctorImplementation"(libs.rootencoder.whip)`.

- [ ] **Step 2: Declare the service and permissions in the proctor manifest**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <application>
        <service
            android:name=".proctor.ProctorService"
            android:exported="false"
            android:foregroundServiceType="camera" />
    </application>
</manifest>
```

- [ ] **Step 3: Write the failing retry test**

```kotlin
@Test
fun `retry backoff doubles and caps at thirty seconds`() {
    assertEquals(1_000L, StreamRetry.delayFor(1))
    assertEquals(8_000L, StreamRetry.delayFor(4))
    assertEquals(30_000L, StreamRetry.delayFor(9))
}
```

- [ ] **Step 4: Run it to confirm it fails**

Run: `./gradlew testProctorDebugUnitTest --tests '*StreamRetryTest*'`
Expected: FAIL — unresolved `StreamRetry`.

- [ ] **Step 5: Implement the service and client**

`StreamClient` wraps RootEncoder's WHIP client: front camera, AVC, 640×480, 12 fps, 400 kbps, keyframe every 2 s. It exposes `StreamRetry.delayFor(attempt: Int): Long = min(30_000L, 1_000L shl (attempt - 1))`.

`ProctorService` is a `Service` that calls `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_CAMERA)` in `onStartCommand`, acquires a partial wakelock, fetches a token, and starts `StreamClient`. It refreshes the token every `heartbeatSeconds` (20 s) from the same endpoint — that refresh is also the heartbeat that writes `lastSeenAt`, so a live stream and a fresh `lastSeenAt` cannot drift apart. On stream end it retries with `StreamRetry.delayFor`, and after 60 s of failures posts `camera-blocked` with reason `network`. It stops on `ACTION_STOP` and releases the wakelock in `onDestroy`.

- [ ] **Step 6: Run the tests and lint**

Run: `./gradlew testProctorDebugUnitTest lintProctorDebug`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/proctor app/src/testProctor
git commit -m "feat(android): publish exam camera over WHIP from a foreground service"
```

---

### Task 5: Start and stop the stream with the session

**Files:**
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/proctor/ProctorController.kt`
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/ExamScreen.kt`
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/session/ExamSessionTeardown.kt`
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/repository/ExamRepository.kt`
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/data/remote/ExamApiService.kt`
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/proctor/ProctorControllerTest.kt`

**Interfaces:**
- Produces: `class ProctorController(enabled: Boolean, startStream: (String) -> Unit, stopStream: () -> Unit)` with `start(sessionId: String)` and `stop()`; a `Companion` factory `ProctorController.forContext(context: Context)` that returns a no-op controller when `ProctorFlags.ENABLED` is false and otherwise binds to `ProctorService`. `ExamApiService.reportCameraBlocked(sessionId, reason)`.
- Consumes: `ProctorService` (Task 4), `ExamRepository.submit` and `ExamSessionTeardown` (existing).

The `forContext` factory lives in `app/src/main/` and returns the lambdas from a `ProctorBindings` object supplied by the `proctor` source set through a `ServiceLoader`-free seam: a `ProctorBindings.install()` call in `CBTApplication` that exists only in the `proctor` flavor. If that seam proves awkward during implementation, the simpler fallback is to keep `ProctorController` in `app/src/main/` as pure logic and have `ExamScreen` receive it as a parameter, with `MainActivity` constructing the flavor-specific one — choose one, do not build both.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `controller does nothing when proctoring is disabled`() {
    val calls = mutableListOf<String>()
    val controller = ProctorController(
        enabled = false,
        startStream = { calls += "start" },
        stopStream = { calls += "stop" },
    )
    controller.start("session-1")
    controller.stop()
    assertTrue(calls.isEmpty())
}

@Test
fun `controller forwards the session id when enabled`() {
    val calls = mutableListOf<String>()
    val controller = ProctorController(
        enabled = true,
        startStream = { calls += "start:$it" },
        stopStream = { calls += "stop" },
    )
    controller.start("session-1")
    controller.stop()
    assertEquals(listOf("start:session-1", "stop"), calls)
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew testStandardDebugUnitTest --tests '*ProctorControllerTest*'`
Expected: FAIL — unresolved `ProctorController`.

- [ ] **Step 3: Implement the controller**

`ProctorController` holds `enabled`, `startStream`, `stopStream`. `start(sessionId)` calls `startStream(sessionId)` only when enabled; `stop()` likewise. It references no Android class, so it compiles in `standard`.

- [ ] **Step 4: Wire the exam screen**

`ExamScreen` gains a `proctor: ProctorController` parameter (defaulted to a no-op instance so existing call sites and previews keep compiling). Inside it:

```kotlin
LaunchedEffect(sessionId, proctor) {
    proctor.start(sessionId)
    try { awaitCancellation() } finally { proctor.stop() }
}
```

`MainActivity` passes the flavor-specific controller. On the submit path and in `ExamSessionTeardown`, `proctor.stop()` runs before the session store is cleared — the ordering matters, because a stream that outlives its session is exactly the bug the spec forbids.

- [ ] **Step 5: Report blocked cameras**

Add `@POST("student-exams/{sessionId}/camera-blocked") suspend fun reportCameraBlocked(...)` to `ExamApiService` and an `ExamRepository.reportCameraBlocked(sessionId, reason)` that swallows network errors. Call it from the permission-denied path in `ExamScreen` and from the service's failure path.

- [ ] **Step 6: Run both flavors' tests**

Run: `./gradlew testStandardDebugUnitTest testProctorDebugUnitTest assembleStandardDebug assembleProctorDebug`
Expected: PASS; `standard` still has no `ProctorService` in its APK.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/id/sch/mts241alikhlash/cbt app/src/proctor app/src/test
git commit -m "feat(android): tie the camera stream to the exam session lifecycle"
```

---

### Task 6: Supervisor grid page

**Files:**
- Create: `assessment-web/src/features/exam-schedule/components/ExamStreamGrid.vue`
- Create: `assessment-web/src/features/exam-schedule/logic/stream.ts`
- Create: `assessment-web/src/features/exam-schedule/logic/stream.spec.ts`
- Create: `assessment-web/src/features/exam-schedule/api/streamApi.ts`
- Create: `assessment-web/src/features/exam-schedule/types/stream.ts`
- Modify: `assessment-web/src/features/exam-schedule/index.ts`

**Interfaces:**
- Consumes: `GET /exam-schedules/:id/streams` (Task 3).
- Produces: `<ExamStreamGrid :schedule-id="string" />` and `isSeatDead(lastSeenAt: string | null, now: Date, thresholdMs = 60_000): boolean` exported from `features/exam-schedule/logic/stream.ts`.

- [ ] **Step 1: Write the failing test**

Test the pure predicate, not the component — the component only renders what it returns.

```ts
import { isSeatDead } from '../logic/stream'

it('marks a seat dead after 60 seconds without a heartbeat', () => {
  const now = new Date('2026-10-30T08:00:00Z')
  expect(isSeatDead('2026-10-30T07:58:59Z', now)).toBe(true)
  expect(isSeatDead('2026-10-30T07:59:01Z', now)).toBe(false)
  expect(isSeatDead(null, now)).toBe(true)
})
```

Place it in `assessment-web/src/features/exam-schedule/logic/stream.spec.ts`.

- [ ] **Step 2: Run it to confirm it fails**

Run: `cd assessment-web && npx vitest run src/features/exam-schedule/logic/stream.spec.ts`
Expected: FAIL — cannot resolve `../logic/stream`.

- [ ] **Step 3: Implement the predicate and the grid**

`isSeatDead` returns true when `lastSeenAt` is null or older than `thresholdMs`.

The grid renders one card per session, ordered by `studentNis`. A `<video autoplay muted playsinline>` attached to hls.js when `playbackUrl` is non-null; a red dot and "Kamera mati" otherwise. Show `violationCount` and `cameraBlocked`. Poll the endpoint every 15 s; do not open a second transport.

- [ ] **Step 4: Run the tests**

Run: `cd assessment-web && npx vitest run src/features/exam-schedule`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add assessment-web/src/features/exam-schedule
git commit -m "feat(assessment-web): supervisor camera grid for an exam schedule"
```

---

### Task 7: MediaMTX and gateway routing

**Files:**
- Create: `platform-infra/compose/docker-compose.media.yml`
- Modify: `platform-infra/gateway/nginx.ssl.conf` (generated — edit `gateway/generate.mjs` instead)
- Create: `platform-infra/docs/MEDIA.md`

**Interfaces:**
- Consumes: `STREAM_PUBLIC_URL` used by Tasks 2 and 3.
- Produces: `/whip/{sessionId}` (publish) and `/hls/{sessionId}/index.m3u8` (playback) on one HTTPS host.

- [ ] **Step 1: Write the compose file**

`docker-compose.media.yml` runs `bluenviron/mediamtx:latest` with `rtmp: no`, `hls: yes`, `webrtc: yes`, `webrtcEncryption: no`, an `authHTTPAddress` pointing at exam-service's token check, and `record: no` on every path.

- [ ] **Step 2: Add the host to the gateway generator**

In `gateway/generate.mjs`, add one server block for the media host: `/whip/` proxied to the media container, `/hls/` served with `Cache-Control: no-store`, and the same TLS block as the other hosts.

- [ ] **Step 3: Regenerate and self-check**

Run: `node gateway/generate.mjs && node gateway/generate.self-check.mjs`
Expected: PASS, `nginx.ssl.conf` updated.

- [ ] **Step 4: Prove publish is refused without a token, and after the session ends**

Run against the running stack:
`curl -s -o /dev/null -w '%{http_code}' -X POST https://<media-host>/whip/abc`
Expected: `401`.

The `authHTTPAddress` hook must call exam-service with the token's `sub` and `nonce` and accept
only when the session is still `IN_PROGRESS` **and** the nonce equals the stored `streamNonce`.
A token issued before a rotation, or before the student submitted, must be rejected even though
its JWT has not expired. Prove it: start a session, get a token, submit the session, then attempt
the publish and expect `401`.

- [ ] **Step 5: Commit**

```bash
git add platform-infra/compose/docker-compose.media.yml platform-infra/gateway platform-infra/docs/MEDIA.md
git commit -m "feat(infra): media host for exam camera streams"
```

---

### Task 8: Trial runbook

**Files:**
- Modify: `docs/KIOSK.md` (add a "Tanpa Device Owner" section pointing at the limits)
- Create: `docs/TRIAL-CAMERA.md`

- [ ] **Step 1: Write the runbook**

`docs/TRIAL-CAMERA.md` covers: which APK (`proctorDebug`), which `API_BASE_URL_DEBUG`, the parent consent paragraph, the in-app notice text, how to check a seat is live, what to do when a seat is red, and the explicit statement that a student can stop the app from the notification and that this is expected.

- [ ] **Step 2: Add the KIOSK.md note**

State that without Device Owner the status bar stays visible, the FGS notification is reachable, and reboot does not restore the camera.

- [ ] **Step 3: Commit**

```bash
git add docs/KIOSK.md docs/TRIAL-CAMERA.md
git commit -m "docs: camera trial runbook and kiosk limits without device owner"
```
