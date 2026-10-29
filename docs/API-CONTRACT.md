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

| Endpoint | Body | `data` |
|---|---|---|
| `GET /me/exams` | - | `[{id, title, subject_name, starts_at, ends_at, duration_minutes, status}]` |
| `POST /me/exams/{examId}/sessions` | `{access_code, device_id}` | `{session_id, deadline, server_time}` |
| `GET /student-exams/{sessionId}/questions` | - | `[{id, number, type, body_html, choices}]` |
| `POST /student-exams/{sessionId}/answers` | `{question_id, answer, client_id}` | `{id, session_id, student_id, question_id, answer, updated_at}` |
| `POST /student-exams/{sessionId}/submit` | - | `null` |

- `status`: `UPCOMING`, `OPEN`, `IN_PROGRESS`, `FINISHED`.
- `starts_at` dan `ends_at`: ISO-8601 UTC.
- `deadline` dan `server_time`: epoch millis. Klien menghitung deadline lokal dari selisihnya.
- Sesi: `409` bila sudah ada sesi aktif di perangkat lain, `403` bila token ujian salah.
- Jawaban: upsert per `question_id`; `client_id` adalah UUID untuk idempotensi.
- Siswa diidentifikasi dari JWT, tidak pernah dari parameter.
- Kunci jawaban tidak pernah dikirim ke klien.
