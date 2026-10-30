# Exam camera proctoring (design)

## Intent

The owner wants to see the student's face while an exam is running, so a stand-in cannot sit
the exam for them. The camera turns on when the exam session opens and stops when the student
hands in. Nothing runs outside an exam session.

The owner also asked for a camera that records outside exams, 24/7, restarting when the phone
boots, so students can be watched at home. That part is **out of scope and will not be built**:
it records children in their homes with no legal basis for the madrasah and no way to switch it
off, and it drains the battery within hours, which ends the monitoring it was meant to provide.
This document covers exam-time proctoring only.

## Goals

- Front camera streams live to the supervisor web page while a session is `IN_PROGRESS`.
- The camera survives screen-off and the app being sent to the background — including on a
  student's own phone, which needs no Device Owner for this.
- The student cannot stop it silently: any stop turns the seat red within 60 s.
- Camera starts automatically when an exam session opens, and only then. No boot start, no
  always-on service.
- Two build flavors: `standard` ships no camera code at all; `proctor` adds it.
- A missing, denied or failed camera never blocks answering questions.

## Non-goals

- No recording after submit, no background capture outside a session, no always-on service.
- No microphone. Audio is a separate consent problem and is not needed to see a face.
- No face recognition, no attention scoring, no automated cheating verdict. The supervisor watches.
- No recording retention policy beyond the session window; see Privacy.

## Constraints found in the codebase

- `app/build.gradle.kts`: `minSdk 24`, `targetSdk 35`, Compose, no CameraX dependency yet.
- `AndroidManifest.xml` declares only `INTERNET` and `ACCESS_NETWORK_STATE`; no service exists.
- `MainActivity.kt:447` already calls `setLockTaskPackages` and `:448` `setStatusBarDisabled(true)`.
- `KIOSK.md` documents Device Owner provisioning; `KioskPolicy.isSecure` gates the exam.
- The monorepo has **no WebSocket gateway and no SSE** in any service. Live view needs new
  transport; it cannot reuse anything.
- `exam-service` has `RecordViolationsUseCase` and `violationCount` on the session entity, but no
  media endpoint. `core/storage/storage.service.ts` exists in several services.

## Approaches

### A. WHIP publish → MediaMTX → HLS (recommended)

The app publishes WebRTC-HTTP Ingestion Protocol (WHIP) over HTTPS; MediaMTX republishes HLS.
The supervisor page plays `<video>` with hls.js.

- 480p @ 12 fps ≈ 400 kbps per student. 8 trial students ≈ 3.2 Mbps, 30 ≈ 12 Mbps.
- Works from home networks: HTTPS on 443, nothing for a router or CGNAT to block.
- 3–10 s latency. Acceptable: a supervisor reacts in seconds, not milliseconds.
- One static binary for the media server.

### B. WebRTC + SFU (LiveKit)

Sub-second latency, two-way audio possible later. Costs an SFU, TURN for home networks, and CPU
for one encoder per publisher. 30 students is real server load. Rejected for now: latency is not
what limits this feature, and the added operational surface is large.

### C. Periodic snapshots to object storage

Cheapest: reuse `storage.service.ts`, no media server. A still every 15–30 s proves presence but
cannot show *what* a student is doing between frames. Rejected as the primary mechanism; kept as
the fallback when the upload path is blocked (see Failure handling).

## Decision

**A**, on its own — there is no snapshot fallback in v1. Build flavor split is required so the
feature can be trialled on real students before any school depends on it.

The trial runs on students' own phones, at home. That is not "production" in the deployment sense:
the API stays on the existing VPS, media runs on a second small box, and the `proctor` flavor is
distributed as a debug-signed APK to the trial group only. Nothing about the `standard` release
changes until the trial is reviewed.

## Trial outside school (students at home)

The first trial runs on real students at home, before any school deployment. That changes three
things and rules out the LAN-only assumption.

### Transport must be HTTPS only

Home networks block or throttle outbound RTMP 1935, and many student phones sit behind CGNAT.
The publishing path is therefore **HTTPS**, not RTMP:

- The app encodes with RootEncoder, which also ships an SRT and a generic streaming interface;
  publish **WHIP over HTTPS** (RootEncoder `WhipClient`) rather than RTMP.
- MediaMTX accepts WHIP on 443 behind the gateway and republishes HLS.
- One port, one certificate, nothing for a home router to block.

RTMP stays as an option only if a school LAN deployment later proves it cheaper.

### The VPS cannot carry the video

`platform-infra` runs 29 images on one VPS, each service capped at 320m, with 2 GB swap. Adding
30 concurrent 400 kbps streams (12 Mbps sustained, both directions through the same box) is not
a load that box can take, and Cloudflare's proxy does not carry WHIP or HLS segments well.

So the media server does **not** live on the VPS. Two options, both acceptable:

| Option | Where video goes | Cost |
|---|---|---|
| MediaMTX on a second small VPS (2 vCPU, 2 GB) | Direct, school pays ~USD 6/mo | One extra box to operate |
| MediaMTX on the existing VPS, 8 students per trial group | Same box, staggered sessions | Free, but caps the trial size |

For a trial with one class, the second option is enough: 8 concurrent streams is ~3.2 Mbps and
the VPS already runs the API. Decide before the first session by counting trial students.

The API endpoints, the auth, and the database all stay on the existing VPS. Only media moves.

### No Device Owner

The trial runs on students' own phones, which are not Device Owner (provisioning requires a
factory reset). `MainActivity.kt:438` gates the kiosk on `isDeviceOwnerApp`, so on these phones
lock task shows the pinning dialog and the status bar stays visible.

What still works without Device Owner — and this is the part that matters:

| Behaviour | Result |
|---|---|
| Camera survives Home, other apps, screen off | **Yes.** See below |
| Student stops the app from the FGS notification | Possible (Android 13+); seat goes red within 60 s |
| Status bar / notification hidden | No |
| Restart after reboot | No — Android refuses background camera FGS start |

Android 11's [foreground services](https://developer.android.com/about/versions/11/privacy/foreground-services)
restriction is about *starting* a camera FGS from the background; it does not stop one that is
already running. The Android 14 [service type reference](https://developer.android.com/develop/background-work/services/fgs/service-types)
describes `camera` as exactly this: "Continue to access the camera from the background, such as
video chat apps that allow for multitasking."

So once the exam screen has started `ProctorService`, Home and screen-off do not stop the camera.
The remaining escapes are stopping the app from the notification and rebooting. Both are visible:
the seat turns red within 60 s, and a stopped stream is what the supervisor page exists to show.

The trial measures this honestly. It is not enforcement; it is supervision with a visible
tamper signal.

## Build flavors

```kotlin
// app/build.gradle.kts
flavorDimensions += "proctoring"
productFlavors {
    create("standard") { dimension = "proctoring"; applicationIdSuffix = "" }
    create("proctor")  { dimension = "proctoring"; applicationIdSuffix = "" }
}
```

Camera code lives in `app/src/proctor/kotlin/...` and the manifest entries in
`app/src/proctor/AndroidManifest.xml`. Gradle merges them only for `proctor`, so `standard`
contains no `CAMERA` permission, no `ProctorService`, no CameraX classes — the capability is
absent, not disabled.

`applicationIdSuffix` stays empty on both: the trial APK must be installable over the existing
app for students who already have it, and a suffix would create a second app. Both flavors
cannot be installed on one phone at once; that is accepted.

`BuildConfig.PROCTORING_ENABLED` (a flavor `buildConfigField`) lets shared code branch without
importing camera classes.

## Client design

### Service

`ProctorService : Service` in the `proctor` source set.

- `android:foregroundServiceType="camera"`; permissions `FOREGROUND_SERVICE` and
  `FOREGROUND_SERVICE_CAMERA` (required at target 35).
- Started with `startForegroundService` from `ExamScreen` while the activity is visible — the
  only moment a while-in-use permission can be used.
- Notification is `IMPORTANCE_LOW`, `setOngoing(true)`, with a `camera` icon. It cannot be
  removed: the platform requires a foreground service to show one. Without Device Owner the
  status bar is reachable, so the student sees the notification and can use Android 13's
  stop-from-notification control. That is accepted and detected (see Failure handling).
- Acquires a partial wakelock so encoding continues with the screen off, and releases it in
  `onDestroy`.

### No boot restart

The trial runs without Device Owner, and without Device Owner Android refuses to start a camera
foreground service from `BOOT_COMPLETED`. There is therefore **no boot receiver and no
`RECEIVE_BOOT_COMPLETED` permission** in this build. A phone that is rebooted mid-exam keeps its
session on the server (`IN_PROGRESS`) and its answers in Room, but the camera stays off until the
student opens the app again — at which point `ExamRestore` reopens the session and the service
starts as normal.

This is a deliberate scope cut, not an oversight. Adding the receiver later for provisioned
devices is a small change; it must not be written now, because an unused background-start path
that never fires is dead code that looks like a guarantee.

### Pipeline

One stack only: **RootEncoder** (`pedroSG94/RootEncoder`, `RtmpCamera2`). It owns the camera,
the encoder and the RTMP connection together; CameraX is not used at all in the `proctor` source
set, and no `ImageAnalysis`/`MediaCodec`/`MediaMuxer` code is written by hand. Configuration:
front camera, AVC, 480p (640×480), 12 fps, 400 kbps, keyframe every 2 s.

CameraX was considered and dropped: it would mean re-implementing the encoder and RTMP muxer
that RootEncoder already ships, for no gain.

- Publish URL: `https://<media-host>/whip/<sessionId>?token=<short-lived JWT>`. The token comes
  from `POST /student-exams/{sessionId}/stream-token` and expires with the session.
- If the connection drops, retry with backoff; the seat goes red after 60 s without a heartbeat.
  No snapshot fallback exists in v1.

### Session lifecycle

| Event | Action |
|---|---|
| `POST /sessions` succeeds, status `IN_PROGRESS` | Start service, request `CAMERA` if not granted |
| Student submits | Stop service, release wakelock, drop the stream |
| Supervisor exit | Stop service |
| Session `EXPIRED` on restore | Stop service |
| Camera permission denied | Exam proceeds; `camera_blocked` recorded once; supervisor sees "kamera mati" for that seat |

A camera failure must never gate answering. That is a deliberate choice: a broken camera in a
school with 30 devices cannot be allowed to cancel an exam.

## Server design

### What already exists

The grid does not need a new roster table. `GET /exam-schedules/:id/sessions`
(`ExamSessionAdminController`, permission `exam-sessions.read`) already returns one row per
student session with `studentId`, `studentName`, `studentNis`, `status`, `startedAt`,
`violationCount` and `deviceBound`. `ExamSchedule` carries `classroomId` and `startsAt`/`endsAt`.

There is no room or seat concept anywhere in the schema, and no `participantNumber` on
`ExamSchedule`. The grid is therefore keyed by **session**, ordered by `studentNis`, with the
label "Bangku" only if the school later adds seating. Do not invent a seat model for this.

### New endpoints (exam-service)

| Endpoint | Who | Permission | Purpose |
|---|---|---|---|
| `POST /student-exams/{sessionId}/stream-token` | student | `exam-sessions.take` | Short-lived publish token, `403` if session not `IN_PROGRESS` |
| `POST /student-exams/{sessionId}/camera-blocked` | student | `exam-sessions.take` | Record `camera_blocked`, idempotent |
| `GET /exam-schedules/{scheduleId}/streams` | teacher | `exam-sessions.read` | Per-session playback URL + `lastSeenAt` |

Teacher authorisation reuses `exam-sessions.read` exactly as the existing admin controller does.
There is no per-teacher exam assignment entity in the schema, so the design does **not** claim
one; scoping to assigned teachers would be a separate change to access control. What is added
here is an access log line per stream request (session id, user id, time) — the audit trail the
privacy section promises.

`camera_blocked` is **not** added to `ViolationPolicy.COUNTED`. A dead camera is not misconduct
and must not count toward the three-strike submit.

### Liveness

`lastSeenAt` is written by the app, not inferred from the media server. The app posts a heartbeat
to `POST /student-exams/{sessionId}/stream-token` refresh every 20 s while streaming (the same
call returns a fresh token when the old one is near expiry). A seat with no heartbeat for 60 s is
drawn red. MediaMTX exposes no per-path telemetry that exam-service can consume without adding a
hook, and a hook that fires on disconnect would still miss a hung encoder.

### Media server

MediaMTX, on its own small box (see Trial outside school), not on the API VPS:

- WHIP publish over HTTPS from the app, HLS read for the supervisor page.
- Auth hook calls exam-service to validate the publish token.
- Per-path recording **off**. No clip recording endpoint exists and none is planned in this
  scope; the privacy section's retention clause covers only what a supervisor may export later.

### Snapshot fallback is not in this build

The design mentions snapshots as a fallback, but there is no upload endpoint, no bucket, and no
teacher-side media URL for it. Building it doubles the endpoint count and adds a retention job.
**Out of scope for v1**: if WHIP fails, the seat shows red and the supervisor sees a dead camera.
Add snapshots later only if the trial shows WHIP failing often enough to matter.

## Supervisor web

New page in `assessment-web` under `src/features/exam-schedule` (supervision of exam schedules
lives there today): a grid for one schedule, one `<video autoplay muted playsinline>` per session
via hls.js, a red dot when `lastSeenAt` is older than 60 s, and the existing `violationCount`.

The page must be served over the **same origin** as the HLS endpoint, or HLS must be behind the
gateway at a path that is HTTPS. A page on `https://dev-assessment.mts241alikhlash.sch.id` cannot
load `http://<media-host>:8888/...` — mixed content is blocked by the browser. Serve HLS through
the gateway on the media host's own subdomain with a certificate.

## Privacy

This is a child's face, live, on a device in their home during the trial. Required before the
trial, not after:

- Written consent from parents, stating the camera runs only while an exam session is open, on
  the student's own phone, and that the stream is live only and not recorded.
- Streams are live only; recording is off in MediaMTX and there is no record endpoint.
- Access limited to the teachers who supervise that schedule, enforced server-side, one audit
  line per stream request.
- A visible in-app notice on the exam page: "Kamera aktif selama ujian".
- No camera outside an `IN_PROGRESS` session, checked against the server, not just local state.
- Because the trial runs on personal phones, the notice must also tell the student how to stop
  it — the OS allows it, and pretending otherwise would be dishonest.

## Failure handling

| Failure | Behaviour |
|---|---|
| No network | Answers still save locally (existing Room queue); stream retries with backoff |
| WHIP refused (home firewall, CGNAT) | Seat shows red; `camera_blocked` with reason `network`; exam continues |
| OEM kills the FGS (Xiaomi/Oppo/Advan) | Recorded as `camera_blocked`; must be tested on the actual trial phones |
| Camera in use by another app | Retry every 5 s; supervisor sees the seat offline |
| Battery below 10% | Stream stops, `camera_blocked` with reason `battery`; exam continues |
| Student stops the app from the notification | Only possible without Device Owner; recorded as `camera_blocked` |

## Verification

- `testStandardDebugUnitTest assembleStandardDebug` builds, and the APK contains no
  `ProctorService` and no `CAMERA` permission (check with `aapt dump permissions`).
- `testProctorDebugUnitTest assembleProctorDebug lintProctorDebug` pass.
- On a normal (non-Device-Owner) phone, the trial condition: session opens → camera starts;
  Home pressed → **stream continues**; screen off → **stream continues**; app stopped from the
  notification → seat red within 60 s; reboot → seat red, camera returns only when the app is
  opened again; submit → stream ends.
- 8 real phones on home networks, one class: every seat reaches HLS within 10 s of session start,
  no seat below 12 fps, total egress under 4 Mbps.

## Open questions

1. Does the trial use the existing VPS for media (8 students max) or a second small VPS? Decides
   cost and whether the trial can run a full class.
2. Are trial students' phones mostly Android 13+? Determines how often the stop-from-notification
   escape actually appears.
3. Do students have unlimited data? 400 kbps for two hours is ~360 MB per exam, which matters on
   a prepaid plan and is worth saying before the trial.
