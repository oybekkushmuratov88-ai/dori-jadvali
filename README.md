# Dori jadvali

Dorilarni har kuni "Ichdim / Ichmadim" deb belgilab borish uchun telefonga o'rnatiladigan ilova (PWA).
iPhone (Safari) va Android (Chrome) brauzerida ochilib, "Uy ekraniga qo'shish" orqali oddiy ilova kabi o'rnatiladi va internetsiz ham ishlaydi.

## Imkoniyatlar

- **Bugun**: kunlik qabullar ro'yxati, "Ichdim / Ichmadim" tugmalari, navbatdagi va vaqti o'tgan dorilar, davolash kunlari jadvali.
- **Tarix**: belgilangan har bir qabul (qachon belgilangani bilan), dori va holat bo'yicha saralash, Excel (CSV) faylga saqlash.
- **Dorilar**: dori qo'shish va tahrirlash (nomi, dozasi, rasmi, ichish vaqtlari, boshlanish/tugash sanasi).
  Tugash sanasi o'tgan dori jadvaldan o'zi chiqib, "Arxiv dorilar"ga o'tadi.
- **Sozlamalar**: shaxsiy hisob (ro'yxatdan o'tish, kirish, parolni tiklash), umumiy davolash muddati, zaxira nusxa saqlash va tiklash.

## Hisob va ma'lumotlar

- Hisobsiz: ma'lumotlar faqat telefonda saqlanadi (localStorage, rasmlar IndexedDB'da).
- Hisob bilan (email va parol, Firebase Authentication): dorilar, tarix va rasmlar Firestore'da saqlanadi va
  istalgan telefonda shu hisobga kirilganda qaytadi. Internetsiz qo'yilgan belgilar internet ulanganda yuboriladi.
  Hisobsiz kiritilgan ma'lumotlar birinchi kirishda (rozilik so'ralib) hisobga ko'chiriladi.
- Har kim faqat o'z ma'lumotlarini o'qiy va yoza oladi (`firestore.rules`).

Firestore tuzilmasi:

- `users/{uid}`: `course`, `meds` (dorilar ro'yxati), `profile`
- `users/{uid}/marks/{YYYY-MM}`: shu oydagi belgilar (`m` xaritasi, kalit `sana|doriId|vaqt`)
- `users/{uid}/photos/{photoId}`: dori rasmi (`d`, JPEG data URL)

## Firebase sozlash

1. console.firebase.google.com → yangi loyiha.
2. Authentication → Sign-in method → Email/Password → Enable.
3. Firestore Database → Create database (production mode) → Rules: `firestore.rules` mazmunini joylab, Publish.
4. Project settings → Your apps → Web app qo'shish → `firebaseConfig` qiymatlarini `firebase-config.js` ga yozish.

`firebase-config.js` da `null` qolsa, ilova hisobsiz rejimda ishlaydi.
Lokal sinov: Firebase emulyatorlari (auth 9099, firestore 8080) va `http://localhost:8765/?emu`.

## Telefonga o'rnatish fayllari

- `dori-jadvali.apk`: Android ilovasi (Android 7.0+). Ichida shu sayt ochiladi, kamera va fayl ulashish ishlaydi.
  Yig'ish: `KEYSTORE=... KEYSTORE_PASS=... android/build.sh` (Android SDK kerak emas, vositalar Maven Central'dan olinadi).
  Imzo: v1 + v2 (`android/tools/sign_apk.py`, Python `cryptography` paketi kerak). Imzo kaliti repoga qo'yilmaydi. Yangilanishni o'rnatish uchun APK har doim o'sha kalit bilan imzolanishi kerak.
- `dori-jadvali.mobileconfig`: iPhone uchun uy ekraniga "Dorilar" ikonkasini qo'shadigan profil (Web Clip).

## Fayllar

- `index.html`: butun ilova (HTML, CSS, JS bitta faylda)
- `sw.js`: internetsiz ishlash uchun service worker (yangilashda `CACHE` versiyasini oshiring)
- `manifest.webmanifest`, `icons/`: o'rnatish uchun ikonka va sozlamalar
- `firebase-config.js`, `firestore.rules`: hisob tizimi sozlamalari va ma'lumotlarga kirish qoidalari
- `vendor/`: Firebase JS SDK 12.19.0 compat build'lari (Apache License 2.0)
- `fonts/`: Golos Text va Unbounded shriftlari (SIL Open Font License 1.1)

## Joylash

Istalgan statik HTTPS hostingda ishlaydi, masalan GitHub Pages: Settings → Pages → Branch: `main`, papka `/ (root)`.
