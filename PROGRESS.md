# Imagine · 開發進度

本檔同步 `~/.claude/projects/.../memory/project_imagine.md`。
跨電腦繼續開發時，clone 此 repo 後從這份文件接續即可。

---

## 路徑 & 部署

- **本機**：`D:\Backup\Desktop\CODE\project\xai_imagine_android\`
- **GitHub**：<https://github.com/za869765/imagine>（private repo）
- **包名**：`com.za869765.imagine`（debug suffix `.debug`）
- **目標裝置**：Galaxy S22 Ultra · Android 16 / One UI 8.0
- **SDK**：compileSdk=35 / targetSdk=35 / minSdk=26

## ⚠️ 沒裝 Android Studio — Build 流程

**唯一 build pipeline 是 GitHub Actions**（`.github/workflows/build.yml`）：

```powershell
# 觸發 build (push 後自動觸發)
git push

# 看最近 runs
gh run list --limit 5

# 下載最新成功 build 的 APK
gh run download <run-id> -n imagine-debug-apk
# → 解壓 zip 得到 app-debug.apk → 傳手機側載
```

- Workflow 用 JDK 17 + `gradle/actions/setup-gradle@v4` + Gradle 8.10.2
- Build 時間：首次 ~7 分鐘（cold cache），後續 ~1-2 分鐘
- 或瀏覽器：<https://github.com/za869765/imagine/actions>

## 技術棧

- Kotlin 2.0.21 + AGP 8.7.3
- Compose BOM 2024.12.01 / Material 3（紫色 M3 主題，Light/Dark 完整）
- Retrofit 2.11 + OkHttp 4.12 + Kotlinx Serialization 1.7
- EncryptedSharedPreferences + BiometricPrompt
- Coil 3.0 + Media3 ExoPlayer 1.5
- Navigation Compose 2.8

## 已完成功能

### 5 個 xAI Imagine 端點全接通

| # | Endpoint | 功能 |
|---|----------|------|
| 1 | `POST /v1/images/generations` | 文生圖（1-4 張，1k/2k） |
| 2 | `POST /v1/images/edits` | 圖片編輯（最多 3 張參考圖） |
| 3 | `POST /v1/videos/generations` | 影片生成（文生影/圖生影/多參考圖生影） |
| 4 | `POST /v1/videos/edits` | 影片編輯 |
| 5 | `POST /v1/videos/extensions` | 影片延長 |
| - | `GET /v1/videos/{id}` | 狀態輪詢（5 秒/次，最多 5 分鐘） |

### 14 個畫面
Splash / PinSetup / ApiKeySetup / Lock / GenerateImage / GenerateVideo / Edit / History / HistoryDetail / Settings / ApiKeyEdit / ChangePin + 3 個 Dialogs（Budget / Moderation / ClearData）

### 安全
- **PIN**：PBKDF2-HMAC-SHA256 100k iters，**長度不洩漏**（點點只長不縮 + ✓ 確認鍵）
- **可變長度** PIN（3-12 位，預設 3）
- 生物辨識（BiometricPrompt，必須 FragmentActivity）
- **切背景立即鎖**（AppLockManager + ProcessLifecycleObserver，NavHost 觀察 lockedState）
- `FLAG_SECURE` 防截圖（可在 Settings 關閉）
- `allowBackup=false` + `dataExtractionRules` 全排除 → **解除安裝完全清資料**

### 預算
- 樂觀記帳：tentative 先扣，API error 自動 refund
- 三層顯示：頂部 BudgetBar + 生成按鈕上方預估 + Settings 內明細
- 達上限可鎖定生成、每月 1 號自動重設
- 編輯預算用 BudgetEditDialog 數字鍵盤

### 媒體
- PhotoPicker（`ActivityResultContracts.PickVisualMedia`，支援 ImageOnly/VideoOnly）
- 本地 Uri 轉 `data:image/...;base64,...` 送給 xAI
- 自動存相簿（MediaStore，存 `Pictures/Imagine/` 跟 `Movies/Imagine/`，免 storage 權限）
- ExoPlayer 嵌入預覽（AndroidView 包 PlayerView）

### 站內跨頁
- 圖片結果「動起來」/「編輯」chip → `currentBackStackEntry.savedStateHandle` 傳 URL（key: `init_media_uri`）
- 目標畫面從 `previousBackStackEntry.savedStateHandle` 取，讀完立刻 remove
- GenerateVideo / Edit 接 `initialMediaUri: Uri?` 入口參數

## 未完成（下次接著做）

| # | 項目 | 為什麼 |
|---|------|--------|
| 1 | 真實歷史紀錄（讀本機 `Pictures/Imagine/` + `Movies/Imagine/` via MediaStore） | 歷史頁仍是 15 筆樣本資料 |
| 2 | **WorkManager 背景輪詢** | 目前影片必須保持畫面開啟才能完成；切背景中斷 |
| 3 | 影片完成系統通知 | 配合 WorkManager 一起做 |
| 4 | Noto Sans TC / Roboto Mono 字型 bundle | 目前用 system fallback |
| 5 | 變更語言（Settings 內語言 row） | 顯示用但沒實際切換 |
| 6 | 主題切換（系統/淺/深 三選） | 目前跟隨系統 |

## 計費（xAI 官方統一價，無解析度分階）

- 圖片：**$0.05 / 張**
- 影片：**$0.05 / 秒**（= $3/分鐘）

## 重要慣例與雷區

- ⚠️ **MainActivity 必須是 FragmentActivity**（BiometricPrompt 需要 FragmentManager）
- ⚠️ **ImagineCard 一定要 `fillMaxWidth()` + `propagateMinConstraints=true`**（否則 wrap content 排版會擠在左邊、click area 過窄）
- ⚠️ **Bottom Nav 用 `Modifier.windowInsetsPadding(WindowInsets.navigationBars)`** 包外層 Column，否則會被三星手勢列擋住
- ⚠️ **ParamPicker 用 `ModalBottomSheet`** 彈選項，不要用 DropdownMenu
- **`mutableStateOf` 包 prefs 讀取的數值** 才會即時刷新（如 budgetCap, spent, imageCount）
- **Composable 內 `Canvas` 的 lambda 不是 @Composable context**，不能讀 MaterialTheme — 要在 Canvas 外 capture color
- **`androidx.compose.ui.draw.offset` 不存在**，要用 `androidx.compose.foundation.layout.offset`
- **xAI Imagine API 沒有 safety / mode / spicy 參數**，後端強制審核
- **xAI 一把 key 走天下**，建立時只需勾「Imagine」權限

## 取得 API Key

1. <https://console.x.ai/team/default/api-keys>
2. Create → 勾「Imagine」權限（其他不勾）
3. 命名 `imagine-android-app`
4. 複製 `xai-...` → 貼進 APP 設定頁

## 換電腦續做的步驟

```powershell
# 1. 確保 gh CLI 已登入
gh auth login

# 2. clone repo
gh repo clone za869765/imagine
cd imagine

# 3. 讀此 PROGRESS.md + UI.md 接續開發
# 4. 編輯 → push → GitHub Actions 自動 build APK → 下載 → 裝到手機
```

新電腦不需要 Android Studio（GitHub Actions 處理 build）。
唯一需要：`git` + `gh` CLI 已登入。

## 重要 commits（最近）

- `94b45f2` — 完整 xAI API 整合（圖/影/編輯/延長）
- `8a77ef8` — PhotoPicker 整合
- `819bc8d` — 3 大互動 bug 修正（BottomNav 擋住 / ParamPicker 拉不動 / 切背景沒鎖）
- `31b9f5d` — ImagineCard fillMaxWidth + propagateMinConstraints（修排版擠 + click area）
- `ad09f18` — Budget editor dialog + state-backed usage display
- 早期 Phase 1-9 — skeleton + 13 個共用 Composable + 14 畫面
