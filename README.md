# Imagine

xAI Imagine API 的 Android 客戶端（純自用）。
Material 3 / Jetpack Compose / Android 16 / Galaxy S22 Ultra。

## 功能

| 模組 | 端點 | 功能 |
|------|------|------|
| 圖片生成 | `/v1/images/generations` | 文生圖 |
| 圖片編輯 | `/v1/images/edits` | 修圖（最多 3 張參考圖） |
| 影片生成 | `/v1/videos/generations` | 文生影 / 圖生影 / 多參考圖生影 |
| 影片編輯 | `/v1/videos/edits` | 改影片內容 |
| 影片延長 | `/v1/videos/extensions` | 接續最後一幀延長 |

加上：PIN 鎖 + 生物辨識、預算追蹤、暗黑模式、Material You 動態配色。

## 取得 APK

1. **GitHub Actions（推薦）** — push code 後自動 build debug APK
   - 進 GitHub repo → Actions tab → 點最新 build → Artifacts 下載 `imagine-debug.apk`
   - S22 Ultra 開「允許未知來源」→ 安裝
2. **Android Studio** — Open 此專案 → Run → 直接裝到 USB 連接的裝置

## 首次設定

1. 開 APP → 設定 PIN（數字，不限長度，建議 4+ 位）
2. 啟用指紋/臉部辨識（可選）
3. 設定頁 → API Key → 貼上 `xai-...`
   - 取得 Key: <https://console.x.ai/team/default/api-keys>
   - 建立時勾選 **Imagine** 權限即可
4. 設定預算上限（預設 $20/月）
5. 開始生成

## 技術

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3
- **HTTP**: Retrofit 2 + OkHttp 4
- **儲存**: EncryptedSharedPreferences（硬體 Keystore 加密）
- **影片**: WorkManager 背景輪詢 + Media3 ExoPlayer 播放
- **圖片**: Coil 3
- **目標 SDK**: 35 (Android 15) — 升級 SDK 36 (Android 16) 請見 Phase 1 升級指南
- **最低 SDK**: 26 (Android 8.0)

## 安全

- API Key 用 EncryptedSharedPreferences 加密儲存（硬體 TEE / Knox 等級）
- APP 切到背景立即鎖（重新進入需 PIN/生物辨識）
- `FLAG_SECURE` 防止截圖與 APP 列表預覽
- `allowBackup=false` + `dataExtractionRules` 防止資料隨 Auto Backup 外洩
- 卸載 APP = 系統自動清除所有資料（包含加密 API Key）

## 計費

xAI Imagine API 計費（May 2026 官方）：
- 圖片：$0.05 / 張
- 影片：$0.05 / 秒

APP 內以本機追蹤估算用量；以 xAI 後台帳單為準。

## 開發

```bash
./gradlew assembleDebug      # 產生 debug APK
./gradlew installDebug       # 裝到連接的裝置
./gradlew lint               # Lint 檢查
```

## 設計

UI 設計規格 → [UI.md](UI.md)
設計原型（HTML/JSX 參考用）→ `design/untitled/project/`
