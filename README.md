# Dori jadvali

Dorilarni har kuni "Ichdim / Ichmadim" deb belgilab borish uchun telefonga o'rnatiladigan ilova (PWA).
iPhone (Safari) va Android (Chrome) brauzerida ochilib, "Uy ekraniga qo'shish" orqali oddiy ilova kabi o'rnatiladi va internetsiz ham ishlaydi.

## Imkoniyatlar

- **Bugun**: kunlik qabullar ro'yxati, "Ichdim / Ichmadim" tugmalari, navbatdagi va vaqti o'tgan dorilar, davolash kunlari jadvali.
- **Tarix**: belgilangan har bir qabul (qachon belgilangani bilan), dori va holat bo'yicha saralash, Excel (CSV) faylga saqlash.
- **Dorilar**: dori qo'shish va tahrirlash (nomi, dozasi, rasmi, ichish vaqtlari, boshlanish/tugash sanasi).
  Tugash sanasi o'tgan dori jadvaldan o'zi chiqib, "Arxiv dorilar"ga o'tadi.
- **Sozlamalar**: umumiy davolash muddati (boshlanish va tugash sanasi), zaxira nusxa saqlash va tiklash.

Ma'lumotlar faqat telefonning o'zida saqlanadi (localStorage, rasmlar IndexedDB'da).
Boshlang'ich dorilar ro'yxati `Dori_qabul_qilish_28_kunlik_jadval.docx` retseptidan olingan.

## Fayllar

- `index.html`: butun ilova (HTML, CSS, JS bitta faylda)
- `sw.js`: internetsiz ishlash uchun service worker (yangilashda `CACHE` versiyasini oshiring)
- `manifest.webmanifest`, `icons/`: o'rnatish uchun ikonka va sozlamalar
- `fonts/`: Golos Text va Unbounded shriftlari (SIL Open Font License 1.1)

## Joylash

Istalgan statik HTTPS hostingda ishlaydi, masalan GitHub Pages: Settings → Pages → Branch: `main`, papka `/ (root)`.
