# Scan Image APP

> Lightweight Android document scanner — pick from gallery or camera, adjust **4 corner points** (like Adobe Scan), auto-straighten perspective, enhance contrast, and save clean results that look like text on white paper.

- **Package ID:** `id.arizu.scanimage`  
- **Min SDK:** 23 (Android 6.0)  
- **Target/Compile SDK:** 35 (Android 15)  
- **UI:** Jetpack Compose + Material 3  
- **Camera:** CameraX  
- **Status:** Open Source 🎉

---

## ✨ Features

- 📷 **Camera & Gallery**: capture with CameraX or pick from gallery  
- 🟩 **Manual 4-point crop**: drag four handles to outline the document  
- 📐 **Perspective correction**: warp the selection to a flat rectangle  
- 🧼 **Clean paper look**: grayscale + contrast boost for crisp text  
- 💾 **On-device saving**: offline, no servers  
- 🔗 **Share & Delete**: quick actions in dashboard  
- 🌓 **Modern UI**: Compose + Material 3  

---

## 🚀 Getting started

```bash
git clone https://github.com/arizu-id/scanimage.git
cd scanimage
```

---

## 📲 How it works

1. **Pick a source** — camera or gallery  
2. **Adjust 4 points** — outline your document  
3. **Scan** — straighten perspective + enhance  
4. **Save** — file stored in device media storage  
5. **Manage** — share or delete from dashboard  

---

## 🛠 Tech stack

- Kotlin 1.9.25  
- Android Gradle Plugin 8.6.1 (Java 17)  
- Jetpack Compose (BOM `2024.09.03`)  
- Compose Compiler 1.5.15  
- Material 3, Lifecycle 2.8.6  
- CameraX 1.3.4  
- Coil 2.6.0  

---

## 💾 Storage & file naming

- **Images (JPG):** saved in `Pictures/ScanImage`  
- **Filename format:** Example: `ScannedDocument_01-10-2025_1696168456123.jpg`

  ---

## 🔐 Permissions

- `CAMERA` — required for taking pictures  
- `READ/WRITE_EXTERNAL_STORAGE` — legacy only (API ≤ 28)  
- On Android 10+ → uses scoped storage (`MediaStore`)  

