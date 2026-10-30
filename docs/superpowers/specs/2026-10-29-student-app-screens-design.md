# Student app screens (short design)

## Intent

After the login moved to web parity (see `2026-10-29-web-parity-login-design.md`), the rest of the app follows the same tokens and the web's card, dialog and header patterns. The owner iterated each screen on a real phone and approved the result on 2026-10-29.

## Goals

- Every screen reuses `WebTokens`, `SectionCard`/`SectionedCard`, `AppDialog` and `FloatingTextField`; no Material text fields, dialogs or icons.
- Card headers are 11sp bold uppercase on a muted band with a hairline below.
- Dialogs follow the web `DialogContent size="md"`: 40dp header and footer, close X at 12dp, 80% scrim.

## Non-goals

- No staff features and no supervisor entry on the token page; supervision stays on the web.
- No participant number, room or desk generation here; the app shows `participantNumber` when the server sends it and "Belum ditentukan" in the profile until that project exists.

## Screens

- **Home:** square drawer, school logo in the top bar, floating reload button. The drawer header is left-aligned: avatar and name, a pill with the exam status (running, open, next or none), NIS, class and semester with academic year, and a connection dot at the top right (red with "Offline" when there is no network). The menu is Daftar Ujian, Jadwal Ujian, Profil Saya, Tata Tertib and Panduan Aplikasi, attached to the header with no gap. Tata Tertib opens with the violation limit; Panduan Aplikasi follows the exam from sign-in to hand-in. Both are numbered cards without intro text. Keluar is an outlined red button at the bottom that asks for confirmation. The list keeps the stale list with a message when a reload fails.
- **Exam list:** groups "Bisa dikerjakan", "Akan datang", "Selesai", one card each; rows expand except finished ones; title is `JENIS - Mapel` (`displayTitle`); upcoming rows show "Dibuka dalam N".
- **Jadwal Ujian:** one card per exam type in the order UTS, UAS, UH, Quiz, Tugas, then others; inside, a day subheader and one row per subject; tapping a row opens a detail dialog. Finished exams are left out.
- **Token page:** fixed hero, token card, "Detail ujian" card; system back returns to the list.
- **Exam page:** header is the logo and the timer only; "Detail soal" card (exam, question type, status, answered count, "Daftar Soal", submit); one card holding "SOAL n" and "JAWABAN"; three equal bottom buttons; "Daftar Soal" dialog with a card per question type, ten numbers per row and a legend footer. The whole essay box focuses the keyboard.
- **Lock page:** one card "Yang perlu dilakukan", asterisk note, "Kunci Lagi".
- **Profile:** identity card with banner and avatar, then Data Siswa, Data Ujian, Data Kelas (Kelas, Tingkat, Semester, Tahun ajaran), Data Diri, Kontak. Rows with no value are hidden; the exam-assignment rows show "Belum ditentukan".

## Server fields the app reads

`/me/exams`: `subjectName`, `type`, `participantNumber`. `/students/me`: `user.identifier`, `user.profile` birth data, email and phone (own record only). `/students/me/classroom`: `semester.name`.

## Verification

`testDebugUnitTest assembleDebug lintDebug` pass; looks were checked on the phone by screenshot.
