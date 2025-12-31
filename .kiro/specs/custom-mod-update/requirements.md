# Custom Mod/Texture Update Feature

## Overview
Fitur untuk memudahkan pemain mengupdate custom server mods melalui launcher menggunakan file konfigurasi `txd.txt`.

## User Stories

### US-001: Download dan Update Custom Textures
**As a** player  
**I want to** automatically update custom server textures via launcher  
**So that** I don't need to manually download and install mod files

#### Acceptance Criteria
- [ ] Launcher dapat membaca file konfigurasi txd.txt
- [ ] Launcher mengecek apakah texture sudah ada di device
- [ ] Jika texture ada, overwrite dengan versi baru
- [ ] Jika texture tidak ada, insert/download file baru
- [ ] Progress download ditampilkan ke user

---

## Questions Pending User Clarification

> **IMPORTANT**: Fitur ini memerlukan klarifikasi dari user sebelum implementasi dapat dilanjutkan.

### Q1: Format/Struktur txd.txt
Bagaimana format file txd.txt?
- Apakah berisi list nama file saja?
- Apakah berisi URL download?
- Apakah ada checksum/hash untuk verifikasi?

**Example format yang mungkin:**
```
# Option A: Simple list
texture1.txd
texture2.txd
model1.dff

# Option B: With URL
texture1.txd|https://server.com/mods/texture1.txd
texture2.txd|https://server.com/mods/texture2.txd

# Option C: JSON format
[
  {"name": "texture1.txd", "url": "...", "hash": "..."},
  {"name": "texture2.txd", "url": "...", "hash": "..."}
]
```

### Q2: Lokasi txd.txt
- Apakah txd.txt di-download dari server URL?
- Atau sudah ada di local device?
- Jika dari server, apa URL-nya?

### Q3: Format File Texture
File apa saja yang akan di-handle?
- `.txd` (texture dictionary)
- `.dff` (model files)
- `.col` (collision files)
- Lainnya?

### Q4: Logic Insert vs Overwrite
- "Insert" maksudnya apa? Download file baru?
- "Overwrite" = replace file yang sudah ada?
- Apakah perlu backup file lama sebelum overwrite?

### Q5: Trigger Mechanism
Kapan fitur ini dijalankan?
- Otomatis saat launch game?
- Tombol khusus di UI?
- Otomatis saat buka launcher?
- Manual trigger dari menu?

### Q6: Target Directory
Kemana file mod akan disimpan?
- Sama dengan game files (`getExternalFilesDir`)?
- Folder khusus untuk mods?

---

## Technical Notes

### Existing Infrastructure
- `GameIntegrityManager.kt` - sudah handle download files
- `DownloadManager.kt` - singleton untuk background download
- `GameRepository.kt` - fetch data dari server

### Proposed Architecture
```
ModUpdateManager.kt (new)
├── parseTxdConfig(source: String): List<ModEntry>
├── checkModIntegrity(mods: List<ModEntry>): List<ModEntry>
└── downloadMods(mods: List<ModEntry>, onProgress): Result
```

---

## Status: WAITING FOR USER INPUT
Implementasi akan dilanjutkan setelah user menjawab pertanyaan di atas.
