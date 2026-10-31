# Full English Localization & Pinned Drawer Settings Footer

Pembaruan ini mengubah seluruh bahasa antarmuka aplikasi **Dolphine 7B Local** menjadi **Bahasa Inggris (Full English)** secara menyeluruh dan memperbaiki tata letak *Sidebar Drawer* agar tombol **Inference & RAM Settings** selalu terkunci permanen di bagian paling bawah layar.

## User Review & Critical Decisions

> [!IMPORTANT]
> Dua perbaikan utama yang akan langsung diterapkan:

- **100% English UI & Messages**: Seluruh teks statis (header, hint, tombol, label dialog, *empty state*, pesan sambutan) dan teks dinamis (format tanggal sesi `MMM dd, yyyy • HH:mm`, statistik penggunaan RAM, konfirmasi hapus sesi, dan *toast* notifikasi) diubah sepenuhnya ke Bahasa Inggris.
- **Permanently Pinned Drawer Footer**: Area daftar riwayat sesi dan teks *empty state* dibungkus dalam satu kontainer tengah fleksibel (`layout_height="0dp"`, `layout_weight="1"`) sehingga meskipun daftar riwayat kosong atau sedikit, tombol **Inference & RAM Settings** tidak pernah naik ke atas dan tetap menempel permanen di bagian paling bawah panel samping.

---

## 1. Overview & Core Concept

- **What It Does**: Menstandarkan seluruh teks aplikasi ke dalam Bahasa Inggris dan memastikan struktur *Sidebar Drawer* memiliki *header* tetap di atas, area daftar sesi fleksibel di tengah, serta *footer* pengaturan yang terkunci permanen di dasar layar.
- **Target Audience / Persona**: Pengguna **Dolphine 7B Local** yang menginginkan antarmuka berbahasa Inggris yang konsisten dan navigasi *drawer* yang stabil.
- **Key Value**: Tampilan menu samping tetap rapi dan tombol pengaturan selalu berada di posisi bawah yang ergonomis baik saat daftar riwayat masih kosong maupun saat berisi banyak sesi.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Full English Experience**:
     - *Drawer Header*: `"Chat History"` dengan subjudul `"Automatically saved to local database"`.
     - *Search & Empty State*: `"Search chat sessions..."` dan `"No chat sessions yet.\nSend a message to automatically save your conversation."`.
     - *Drawer Footer*: `"Inference & RAM Settings"` dengan subjudul `"4 Threads • 512 Ctx • 64 Tokens"`.
     - *Delete Confirmation Dialog*: `"Delete Chat Session?"` — `"Are you sure you want to permanently delete \"[Session Title]\" and all of its messages?"` dengan tombol `"Cancel"` dan `"Delete Permanently"`.
     - *Settings Dialog*: Seluruh judul, statistik RAM (`"Available / Total GB"`), estimasi KV-Cache, dan label kontrol sepenuhnya menggunakan Bahasa Inggris.
  2. **Pinned Bottom Settings Button**:
     - Saat *Sidebar Drawer* dibuka dalam keadaan belum ada riwayat percakapan (`0 sessions`), pesan *empty state* tampil di tengah area daftar, sementara tombol **Inference & RAM Settings** tetap berada tepat di bagian paling bawah *drawer*.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision: Pembungkus `FrameLayout` Berbobot (`layout_weight="1"`) untuk Area Daftar Sesi**
  - *Chosen Approach*: Menempatkan `tvEmptyHistory` dan `recyclerViewHistory` di dalam satu `FrameLayout` yang memiliki `android:layout_height="0dp"` dan `android:layout_weight="1"`.
  - *Why*: Sebelumnya, ketika `recyclerViewHistory` disembunyikan (`View.GONE`) saat riwayat kosong, tidak ada lagi elemen dengan `layout_weight="1"` yang menahan ruang tengah, sehingga *footer* pengaturan tertarik naik ke atas. Dengan `FrameLayout` berbobot `1`, ruang tengah selalu terisi penuh dan *footer* pengaturan terkunci permanen di bagian paling bawah.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Sidebar Drawer Layout Hierarchy**:

```
┌────────────────────────────────────────────────────────┐
│ Sidebar Drawer (Vertical LinearLayout, match_parent)   │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Drawer Header ("Chat History", Search, New Chat) │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Middle Container (FrameLayout, height=0dp, wt=1) │  │
│  │  ├─ RecyclerView (Session List)                  │  │
│  │  └─ Empty State TextView (Centered)              │  │
│  │                                                  │  │
│  │  (Always expands to fill all remaining height)   │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Pinned Bottom Footer (Always at very bottom)     │  │
│  │  [⚙ Inference & RAM Settings]                    │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```
