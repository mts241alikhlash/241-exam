# Kontrak API yang diminta klien Android

Dokumen ini adalah permintaan klien. Backend (identity-service dan exam-service) belum
memilikinya; sepakati dengan tim sebelum diimplementasikan.

## Envelope

Semua respons dibungkus `ResponseInterceptor`:

```json
{ "statusCode": 200, "message": "OK", "data": {} }
```

Error: `{ "statusCode": 401, "message": "Kredensial salah", "data": null }`.
Semua field JSON memakai snake_case.

## Autentikasi (identity-service)

| Endpoint | Body | `data` |
|---|---|---|
| `POST /auth/native/login` | `{identifier, password, device_id}` | `{access_token, refresh_token, expires_in, user: {id, name, roles[]}}` |
| `POST /auth/native/refresh` | `{refresh_token, device_id}` | sama dengan login; refresh token dirotasi |
| `POST /auth/native/logout` | `{refresh_token}` | `null` |

- Token dikembalikan di body, tanpa cookie.
- Hanya role `STUDENT` dan `TEACHER` yang diterima.
- `TEACHER` dipakai untuk verifikasi pengawas lewat endpoint login yang sama.
- `expires_in` dalam detik.

## Ujian (exam-service)

Kontrak ini mengikuti `services/contracts/exam/openapi.json`; exam-service memegang kontraknya dan klien
mengikutinya. Field exam memakai camelCase. Kontrak auth di atas tetap snake_case dan belum disepakati.

Semua permintaan mengirim `Authorization: Bearer <access token>` dan `X-Device-Id: <id perangkat>`. Rute
`student-exams/*` menjawab `400` bila header kedua hilang dan `409` bila sesi terikat ke perangkat lain.

| Endpoint | Body | `data` |
|---|---|---|
| `GET /me/exams` | - | `[{id, title, subjectId, startsAt, endsAt, durationMinutes, status, sessionId}]` |
| `POST /me/exams/{examId}/sessions` | `{accessCode, deviceId}` | `{sessionId, deadline, serverTime}` |
| `GET /student-exams/{sessionId}/questions` | - | `[{id, number, type, bodyHtml, points, choices: [{key, html}] \| null}]` |
| `GET /student-exams/{sessionId}/answers` | - | `[{questionId, answer}]` |
| `POST /student-exams/{sessionId}/answers` | `{questionId, clientId, answer}` | `{questionId, clientId, receivedAfterDeadline, updatedAt}` |
| `POST /student-exams/{sessionId}/submit` | - | `{status, submittedAt}` |
| `GET /student-exams/{sessionId}/files/{fileId}` | - | bytes gambar (bukan envelope) |

- `examId` adalah id jadwal. `status`: `UPCOMING`, `OPEN`, `IN_PROGRESS`, `FINISHED`. `sessionId` null bila belum mulai.
- `startsAt`, `endsAt`, `submittedAt`: ISO-8601 UTC. `deadline` dan `serverTime`: epoch millis; klien menghitung deadline lokal dari selisihnya.
- `type`: `MULTIPLE_CHOICE`, `MULTIPLE_RESPONSE`, `TRUE_FALSE`, `SHORT_ANSWER`, `ESSAY`. Urutan soal dan pilihan sudah diacak per sesi.
- `answer` bertipe: `{"key":"B"}` (pilihan ganda), `{"keys":["A","C"]}` (pilihan ganda kompleks), `{"value":true}` (benar/salah), `{"text":"..."}` (isian singkat dan uraian, maksimal 20000 karakter).
- Memulai sesi: `403` token salah, kelas lain, belum dibuka atau sudah berakhir; `429` terkunci lima menit setelah lima token salah; `409` sesi aktif di perangkat lain atau sudah dikumpulkan. Memulai lagi dengan token dari perangkat yang sama melanjutkan sesi yang sama; sesi yang dilepas pengawas dilanjutkan dengan token dari perangkat baru.
- Jawaban: satu per soal, `clientId` unik per revisi (dipakai ulang untuk soal lain ditolak `409`); `clientId` yang sama diulang tidak mengubah apa pun. Kirim berurutan per soal. Diterima sampai deadline + 10 menit (ditandai `receivedAfterDeadline` setelah deadline); sesudahnya `409 Waktu pengiriman jawaban sudah habis`.
- `submit` idempoten. `status`: `SUBMITTED` atau `EXPIRED` bila ada soal isian singkat atau uraian yang menunggu guru, `GRADED` bila semua soal sudah dinilai otomatis.
- Gambar soal ada di HTML sebagai `<img data-file-id="uuid">`; unduh lewat rute `files` sesi yang sama, hanya untuk gambar yang dipakai kertas soal sesi itu (selain itu `404`).
- Siswa diidentifikasi dari JWT, tidak pernah dari parameter. Kunci jawaban tidak pernah dikirim ke klien.
