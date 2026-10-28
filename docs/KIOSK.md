# Kiosk / Device Owner Setup

## Kenapa perlu Device Owner

Tanpa Device Owner, Android **selalu** menampilkan dialog konfirmasi saat aplikasi memanggil
`startLockTask()`, dan siswa dapat menolaknya lalu keluar. Ini perilaku OS, bukan bug aplikasi.
Dengan Device Owner, pinning berjalan senyap dan tombol Back/Home/Recents diblokir.

## Menjadikan perangkat Device Owner

Perangkat harus **belum punya akun** (factory reset) — Android hanya mengizinkan provisioning
Device Owner pada perangkat bersih.

```bash
adb install app-debug.apk
adb shell dpm set-device-owner \
  id.sch.mts241alikhlash.cbt/.kiosk.ExamDeviceAdminReceiver
```

Verifikasi:

```bash
adb shell dpm list-owners
# Device Owner: id.sch.mts241alikhlash.cbt
adb shell dumpsys device_policy | grep -i "device owner"
```

## Perilaku saat Device Owner aktif

- `startLockTask()` berjalan tanpa dialog; tidak ada tombol "Unpin".
- Status bar dinonaktifkan selama ujian, keyguard dimatikan.
- Hanya paket aplikasi ini yang boleh masuk lock task (`setLockTaskPackages`).
- Saat ujian selesai, status bar dan keyguard dikembalikan.

## Tanpa Device Owner

Aplikasi tetap berjalan, tetapi:

- dialog pinning muncul dan bisa ditolak;
- siswa dapat keluar dan mencari jawaban.

Gunakan hanya untuk demo/uji internal.

## Menghapus Device Owner

```bash
adb shell dpm remove-active-admin \
  id.sch.mts241alikhlash.cbt/.kiosk.ExamDeviceAdminReceiver
```
