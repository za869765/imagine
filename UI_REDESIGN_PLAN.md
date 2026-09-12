# Imagine App — UI 修改工作清單（v1.8.5 現況對照版）

依據：`UI.md`（含 2026-09-12「v1.7～v1.8.5 增補」章節）＋《Imagine App 介面修改建議》（ChatGPT 評估）＋《給 Claude Code 的最終修改建議》（Claude Design 合併版）。
本版由 Claude Code 對照 **v1.8.5 原始碼** 逐項核實後改寫：目標已不存在或已完成的項目移除或重寫，其餘保留原意。每項標明 **改什麼 → 動哪些檔 → 驗收標準**。
文末「與前版差異」列出所有更動與理由，供再次審閱。

---

## 0. 執行原則

1. **以 v1.8.5 為基準**：App 已是「xAI + OpenRouter 雙供應商、供應商由模型 id 判斷」、三頁頂部「對話｜生圖｜生影」分段、模型列 `ModelPickerRow`、全 App 強制深色。所有修改建立在這個現況上，不回退。
2. **先流程、後外觀**：Phase 1–3 處理「更快開始生成、看懂處理狀態、方便接著使用成果」；配色、圓角、字級留到 Phase 4。
3. **不為統一而顯示不適用參數**：統一的是「位置、名稱、操作方式」，不是選項集。UI.md 痛點 #7 的 disabled 做法**不採用**。OpenRouter 模型的參數集依 `supported_parameters` 動態決定，不硬寫。
4. **共用元件優先於逐頁修補**：先抽 `ModePicker`、`SourceSlot`、`GenerateSettingsSummary`、`GeneratingCard`、`MediaActionBar`，再讓各頁復用。
5. **導覽標籤與動作文案分開治理**：「對話｜生圖｜生影」是導覽短標籤（三頁頂部分段、首頁三主卡），**保留**；`MediaAction` 只統一結果區、Viewer、歷史詳情、教學頁的動作文案。
6. **對話頁 `ChatScreen` 與模型列 `ModelPickerRow` 本輪不動**（前版清單未涵蓋，且 v1.8.3/1.8.4 剛依實機回饋調整過）。
7. **每個子項一個 commit，CI 綠燈才做下一項；每個 Phase 結束停下實機驗收**。本機無 Android SDK，GitHub Actions 是唯一 build 管道。
8. 沿用既有 token（`ImagineTheme` / `ImagineSpacing` / `ImagineShapes`），新元件不新增硬編碼色。

---

## Phase 1：生成頁重排（最高優先）

### 1.1 AppBar 標題表達位置，清掉最後一處彩底
- 圖片頁彩條已不存在；影片頁只剩組合延長分支的 `🔗 組合延長` 綠底標籤（`GenerateVideoScreen.kt` 約 L498–510，`Color(0xFF0F5E57)`），刪除。
- AppBar 標題改為「生成圖片」「生成影片」「組合延長」（取代通用「Imagine」）。
- 頂部 L1「對話｜生圖｜生影」`SegmentedTab` **保留**（原則 5）。
- ~~品質 SegmentedTab 移入設定~~：已於 v1.8.3 移除，快速／高品質即模型列裡兩個 xAI 模型 id，**無此項**。
- 檔案：`ui/generate/GenerateImageScreen.kt`、`GenerateVideoScreen.kt`、`ui/component/AppBar.kt`。
- 驗收：未生成前，S22U 首屏可同時看到「L1 分段 → 模型列 → 製作方式 → 提示詞 → 主按鈕」，不需捲動（主按鈕見 1.6）。

### 1.2 新增 `ModePicker`，取代 ModePill 橫滑列與 UnderlineTab
- 新元件 `ui/component/ModePicker.kt`：外觀沿用 `ParamPicker`（12dp 圓角、1dp outline、上 label 下值），label「製作方式」，點開 `ModalBottomSheet`，每項＝名稱＋一句用途。
- **影片頁** 5 項（對應現有 `videoFn` + `VideoMode`）：
  - 文字生成影片 — 只用提示詞生成新影片（`gen` + `T2V`）
  - 圖片動起來 — 以一張圖為起始格生成影片（`gen` + `Img2Vid`）
  - 參考圖生影 — 多張參考圖鎖角色／場景，不當第一幀（`gen` + `Ref2Vid`）
  - 延長影片 — 從影片尾格續接（`extend`）
  - 修改影片 — 依說明改動既有影片（`edit`）
  - **供應商規則沿用現況**：OpenRouter 且模型 `frameImages != true` 時隱藏「圖片動起來」；「延長／修改影片」只在 xAI 顯示。不可用的項目**不列出**，並在清單底部一行小字說明「此模型不支援圖生影／延長」。
- **圖片頁** 2 項：「生成圖片／修改圖片」，取代 L2 `UnderlineTab("生圖"/"圖片編輯")`。
- `EditScreen`（History 進入的獨立 wrapper）頂部三段 `SegmentedTab` 也改用 `ModePicker`，三處入口外觀一致。
- 選「延長／修改」時仍掛 `EditPane`，但**移除三處 `return@Column` 早退**（`GenerateImageScreen`/`GenerateVideoScreen`/`EditScreen`），改 `when(mode)` 切換內容區，讓 1.4–1.6 的設定摘要與主按鈕位置固定。
- 驗收：影片頁五種方式在同一控制項內；OpenRouter 下清單自動縮短且有說明；三處編輯入口外觀相同。

### 1.3 來源區「需要時才出現，附更換」
- 「起始圖」「參考圖（可多張）」「來源影片」「尾格」統一為 `SourceSlot` 區塊：僅在模式需要時出現；已選時顯示縮圖 + 「更換」文字鈕（取代右上 ✕），無來源時顯示「選擇圖片／影片」。參考圖生影的多張模式顯示縮圖列 + 「新增」。
- 組合延長分支（`initialExtendBase != null`）**併回主流程**：mode 固定「延長影片」、來源鎖定尾格且無「更換」、多顯示一行「解析度沿用原片」。刪除獨立 UI 分支，但保留 `Routes.COMBINE_EXTEND` 路由與返回行為（硬約束 2）。
- 驗收：組合延長與一般延長使用同一段 Composable，僅差來源鎖定與說明。

### 1.4 `GenerateSettingsSummary`：參數收成一列摘要
- 新元件 `ui/component/GenerateSettingsSummary.kt`：一列可點文字（例：「直式 9:16・720p・6 秒」「1k・16:9・4 張」），點擊展開（`AnimatedVisibility`）現有 `ParamPicker` 群，改 `FlowRow` 排列。
- 圖片頁展開內容：解析度、長寬比、數量（**無品質**）。影片頁：秒數、長寬比、解析度。OpenRouter 模型依 `supported_parameters` 只列可用者。**不顯示對方媒體的參數**。
- 摘要值顯示規則寫在單一 `describeSettings()`，供結果區 meta 復用。
- 驗收：預設收合；展開後 ParamPicker 標籤在 S22U 大字體不截斷。

### 1.5 提示詞欄位行為
- `PromptInput`：移除「獲焦自動全選」與 `suppressSelectAllOnce` 機制（`PromptInput.kt` L82/116/135/152/192/278–285），改一般游標定位。
- 套用範本／片語／AI 填表結果時若欄位已有內容，彈 `AlertDialog`：「取代」「加入末尾」「取消」。
- 工具列常駐「套用範本」「自己組」兩顆；「複製／建議 N/5／跳格」收進右側 `more_vert` 選單（`Icons.kt` 需新增 `more_vert`，目前不存在）。AI 填表／JSON 匯出匯入入口位置不變。
- 驗收：點擊已有文字的欄位不會全選；套範本不會靜默覆蓋。

### 1.6 主按鈕固定底部
- `ImagineScreen`（`ui/component/ScreenFrame.kt`）新增可空 slot `bottomAction: (@Composable () -> Unit)?`，渲染於內容區與 BottomNav 之間，含 `imePadding()` + `navigationBars` inset；內容區底部預留 72dp。
- 生成頁主按鈕移入此 slot，文案依模式：「生成圖片」「修改圖片」「生成影片」「延長影片」「修改影片」；組合延長為「生成續集並接成長片」。
- `ChatScreen` 不使用此 slot（輸入列已固定底部）。
- 驗收：長提示詞不需捲到底即可送出；鍵盤開啟時輸入框與主按鈕同時可見。

---

## Phase 2：狀態、結果、動作統一

### 2.1 `GeneratingCard`：真實階段取代估算百分比
- 新元件 `ui/component/GeneratingCard.kt`，取代三套實作（圖片頁換按鈕字／影片頁詳細卡／`EditPane` 簡卡）。
- 顯示：階段文字 + 已等待時間（`m:ss`）+ 不定進度 `LinearProgressIndicator`。**刪除 `elapsed / estSec` 封頂 97% 的估算進度**（`GenerateVideoScreen.kt` L566–581、L834–848 兩處）。
- 階段由 `runGenerate` / `VideoPollWorker` 既有狀態映射：「已送出」→「等待結果」→「下載中」。`data/work/VideoPollWorker.kt` 目前**沒有** `stage` 欄位，需在 progress `Data` 補一個 `KEY_STAGE`，不改輪詢邏輯與 `trackedRequestId` SSOT。xAI 與 OpenRouter 兩條輪詢分支都要寫入。
- 「可切背景／鎖屏，完成發通知」「請勿從最近應用滑掉」兩句**保留**（硬約束 5），合併成一段輔助文字；圖片編輯（同步路徑）僅顯示階段與時間。
- 驗收：進度條不再長時間停在同一數值；四處生成中狀態外觀一致。

### 2.2 統一媒體動作文案與 `MediaActionBar`
- 新檔 `ui/component/MediaActions.kt`：`enum MediaAction(label, icon)` 為結果區／Viewer／歷史詳情／教學頁的唯一字串來源：
  - 圖片：修改圖片、圖片動起來、儲存到相簿、分享、複製提示詞、加入素材庫、改分類、移出素材庫、存為角色定妝圖
  - 影片：延長影片、修改影片、儲存到相簿、分享、複製提示詞
  - 提示詞：套用提示詞
- 廢除舊稱（僅動作文案）：`HistoryDetailScreen` 的「編輯這張／編輯這段／動起來（生影片）」、`MaterialLibraryScreen` Viewer 的「生影」`ViewerAction`、`EditScreen` 註解。
- **不改**：三頁 L1 分段與首頁主卡的「對話｜生圖｜生影」、`PromptTemplate`/`PromptJson` 內給模型看的提示字串。
- 新元件 `MediaActionBar(primary: List<MediaAction>（固定 2）, more: List<MediaAction>)`：兩顆 `OutlinedActionButton` 並排，其餘收進「更多」`DropdownMenu`。
  - 圖片結果預設主要：修改圖片／圖片動起來
  - 影片結果預設主要：延長影片／儲存到相簿
  - Viewer 內：底部固定兩顆膠囊 + 右上「更多」；現有「≤4 全顯示、>4 前 3 + 更多」邏輯改為固定 2 + 更多。
- 套用處：圖片頁結果區、影片頁與 `EditPane` 結果區、`FullscreenImageViewer.ViewerAction`、`HistoryDetailScreen.ActionRow`、素材庫 Viewer、教學頁課程圖／影動作。
- 歷史詳情「套用提示詞」：影片項目帶回**影片流程**（現為一律回文生圖）。
- 驗收：`grep` 動作文案處找不到舊稱；每種媒體任一入口的主要動作相同；導覽標籤未變。

### 2.3 結果區多張圖用網格
- 圖片頁結果區：`resultUrls.size > 1` 時改 3 欄方形網格（非 Lazy，避免巢狀捲動）；單張維持大圖。
- 點擊開 `FullscreenImageViewer`，Viewer 內動作作用於「目前這張」，頁碼徽章保留。
- 「整批加入素材庫」分類 chip 群加標題「整批 N 張加入素材庫」，與單張動作以 `SectionHeader` 分隔。
- 驗收：一次生成 10 張不再垂直堆疊 10 張大圖；單張／整批作用範圍文字明確。

### 2.4 移出素材庫 vs 永久刪除
- 「移出素材庫」（只取消分類、檔案仍在）：取消 `AlertDialog`，改 Snackbar + 「復原」（5 秒）。全 App 目前**沒有** `SnackbarHost`，在 `ImagineScreen` 加一個共用 host。
- 「永久刪除」（History 多選刪除）：保留 `AlertDialog`，標題明寫「永久刪除 N 個檔案」，按鈕用 `error` 色。
- Viewer 中「移出素材庫」已是 `visibility_off`（v1.8），維持。
- `HistoryDetailScreen.CategoryPickerRow`：改成可見 toggle（選中填色＋叉），移除「再點同一個取消」隱性手勢。
- 驗收：兩種動作文案與視覺明顯不同；移出可復原。

---

## Phase 3：其他畫面

### 3.1 首頁 `MaterialHubScreen`：增加「繼續工作」
- **保留** v1.8 的三張 `PrimaryGenCard`（對話／生圖／生影）與三列 `ToolTile`（素材庫／素材總覽・去留審查／Grok）；不回退成兩張漸層卡。
- 在三主卡之下、工具列之上新增兩區：
  1. **進行中的任務**：讀 `VideoPollWorker` 追蹤中的 `trackedRequestId`，以 `GeneratingCard` 精簡版顯示，點擊回該生成頁；無任務時整區不顯示。
  2. **最近成果**：History 最新 6 項，3 欄縮圖，點開 `HistoryDetailScreen`；右側「所有作品」文字鈕。
- 主卡副標若仍是點分隔長字串，改 `ImagineChip` 群。
- 保留所有入口 callback 與路由（硬約束 2）。

### 3.2 歷史與素材庫：說清楚差別
- `HistoryScreen` AppBar 標題與各處入口文案改為「所有作品」。
- `MaterialLibraryScreen` 頂部說明改為「可重複使用的參考圖片，供圖片動起來／修改圖片／參考圖生影時選用」。
- 互相前往：所有作品 AppBar trailing 加「素材庫」文字鈕；素材庫加「所有作品」。
- 歷史 ⭐ 角標與 filter 改為「已加入素材庫」標記（圖示 `bookmark_added` 或 `folder`，需註冊到 `Icons.kt`），與範本 ★ 收藏區分。

### 3.3 長片組合 `LongVideoScreen`
- 常駐「銜接技巧」卡（L143–160）改為 AppBar 右側 `help` 圖示 → `AlertDialog` 顯示四條技巧。
- 「加入」時即以 `VideoMerger` 的相容性判斷（解析度／編碼）檢查；不相容則 `AvailRow` 標示「解析度不同，需先轉檔或延長」並禁用加入。
- 合成成功後**不清空**組裝條；加「開始新組合」文字鈕手動清空。
- 移除重複的 `SectionHeader("長片組合")`（L135）；`SectionHeader` 內全形 ①②③（L172/237/310）改純文字。~~刪 `IconBtn` 死碼~~：已不存在。
- 縮圖抽幀加 `LruCache<String, Bitmap>`（不引入新函式庫）。

### 3.4 教學範本 `TutorialScreen`
- `ReadyPromptCard` 改為：縮圖（若無則 `surfaceVariant` 佔位）+ 範本名稱 + 適用類型 chip（文生圖／文生影／圖片動起來）+ 主按鈕「使用範本」；完整提示詞預設摺疊，點「查看提示詞」展開。
- 需來源圖片的範本（i2v）：「使用範本」後帶 prompt 到影片頁「圖片動起來」模式並**直接開啟 `LibraryImagePickerSheet`**。
- `CatChip` 併入 `ImagineChip`；`ActionRow`／`LessonCard` 改用 `ImagineCard` + `MediaActionBar`。
- 課程圖／影動作文案依 2.2 改為「修改圖片／圖片動起來」「修改影片／延長影片」。

### 3.5 設定 `SettingsScreen`：五組入口
- 首層只顯示五張 `ImagineCard` 入口（icon + 標題 + 副標 + chevron），各進次級頁（同檔以 `section` 狀態切換，不新增路由）。對應現有區段：
  1. 生成預設 — 「生成預設」（品質已於 v1.8.4 移除，不再出現）
  2. API 與用量 — 「API」（xAI + OpenRouter 兩把 key）、「帳單 / 後台」（含 OpenRouter 餘額／活動紀錄）
  3. 儲存與資料 — 「素材」（去留審查、從雲端更新素材）、「歷史匯入」、「Keys 備份」
  4. 進階與疑難排解 — 「安全」（FLAG_SECURE 等，**無 PIN**）、「背景任務」、「除錯」、「危險區」（仍預設摺疊）
  5. 關於與更新 — 「更新」、「關於」
- 所有區段統一 `ImagineCard(pad=0)` 包 `SettingRow`；危險區改用同結構但 `errorContainer` 底色，移除手刻 `Box`。
- `console.x.ai` team id、Grok UA 字串抽到 `Constants.kt`；`formatApkSize` 只剩一份，改為 internal 共用即可。

### 3.6 Grok 諮詢
- AppBar 下方加一條 12sp `surfaceVariant` 狀態列「外部網頁 · 以你的 Grok 帳號登入」。
- 右上加「複製回提示詞」文字鈕：讀剪貼簿最新內容帶回生成頁 `PromptInput`（走 1.5 的取代／加入末尾對話框）。

---

## Phase 4：視覺收斂（流程驗收通過後才做）

- 硬編碼 sp／dp 收斂到 `ImagineTypography` / `ImagineSpacing` / `ImagineShapes`；PrimaryButton 28dp、Chip 18dp、Card 16dp 維持。
- 移除 `showBalanceBar` 參數（10 處引用）與其他死碼；`Routes.VIDEO_GENERATING` 無人使用，一併移除。
- **只檢查深色**（全 App 強制深色）：新元件（`ModePicker`、`GeneratingCard`、`MediaActionBar`、`GenerateSettingsSummary`）對比度 ≥ 4.5:1。`BadgePill` 硬編碼色本輪不動。

---

## 五、驗收流程（每個 Phase 結束都跑）

主流程：**輸入提示詞生成圖片 → 選一張變成影片 → 延長並組合長片。** 用 xAI 與 OpenRouter 各跑一次。

必檢項目：
1. 每一步畫面上都找得到下一個動作（結果區主要動作兩顆可見）。
2. 返回、切換 tab、切背景再回來，提示詞草稿與來源不丟失；不相容來源有明確提示。
3. 生成中只顯示真實階段＋已等待時間，無估算百分比。
4. 長提示詞下主按鈕仍可見；鍵盤開啟時輸入框與主按鈕同時可見。
5. 單張動作與整批動作標題明確。
6. 長片組合：加入片段時即知是否相容；合成成功後排列保留。
7. 動作文案處 `grep` 不到「編輯這張」「編輯這段」「動起來（生影片）」；導覽標籤「對話｜生圖｜生影」仍在。
8. `VideoPollWorker` 跨 process 恢復、通知、背景下載存相簿行為與修改前相同（xAI 與 OpenRouter 兩分支）。
9. OpenRouter 模型切換後，製作方式清單與參數集正確縮減。

---

## 硬約束（不得破壞）

1. Jetpack Compose + Material 3；沿用 `ImagineTheme` token 與三個 CompositionLocal；不引入其他 UI 框架。
2. 底部三 tab 與既有路由（含 `CHAT`、`MATERIAL_REVIEW`、`COMBINE_EXTEND`）、首頁三主卡＋三工具入口、三頁互切錨定 `MATERIAL_HUB`（`popUpTo` + `saveState/restoreState`，不可錨 `GENERATE_IMAGE`）、編輯／延長／組合延長進入與返回行為（含 `BackHandler`、Grok WebView `goBack`）、`trackedRequestId` 作為 SSOT 的跨 process 狀態還原。
3. Edge-to-edge：`enableEdgeToEdge()`、內容區 `.imePadding()`、PromptInput `bringIntoViewRequester`、Viewer 底部 inset 推高、`ImagineScreen.scroll` 旗標約定（內嵌 `Lazy*` 必傳 `scroll=false`）。新增的 `bottomAction` slot 也必須吃 ime／navigationBars inset。
4. 全介面繁體中文；本文件的文案統一屬於「整併」，不是改語言。
5. `runGenerate`、`VideoPollWorker`（xAI／OpenRouter 兩分支）、`appScope` 背景下載、`resultGen++` 快取 key、圖片編輯同步路徑 vs 影片 Worker 路徑分工；「可切背景／鎖屏、完成發通知」「請勿從最近應用滑掉」提示保留。
6. 單裝置、單帳號、本機儲存；Keys 備份僅走系統分享 CSV（含 OpenRouter key）。
7. 不新增重型相依：`MediaMuxer`/`VideoMerger`、Media3 ExoPlayer、Coil 維持；版本號只 bump `app/build.gradle.kts` 一處。
8. **v1.8 新增**：供應商由 `ApiProvider.ofModel(id)` 判斷（含 `/` = OpenRouter），不再有「目前供應商」切換；`ModelPickerRow` 三頁共用且模型不在清單自動退回預設；動作列在 384dp 大字體下用 `FlowRow` 不用固定欄；`ModalBottomSheet` 彈選項不用 `DropdownMenu`（`MediaActionBar` 的「更多」是例外，項目 ≤ 6 且純文字）；全 App 強制深色，`darkTheme` 參數保留不用。

---

## 與前版差異（供再次審閱）

| 前版項目 | 本版處理 | 理由（對照 v1.8.5 原始碼） |
| --- | --- | --- |
| 1.1 移除模式彩條 | 縮小為刪影片頁組合延長一處綠底 | 圖片頁彩條已不存在 |
| 1.1／1.4 品質移入生成設定 | **刪除** | 品質 SegmentedTab 已於 v1.8.3 移除，快速／高品質 = 模型列兩個 xAI 模型 |
| 1.2 ModePicker 4 項 | 改 5 項，補「參考圖生影」與 OpenRouter 縮減規則 | 現有 `ModePill` 已是單排 5 選 1；`VideoMode.Ref2Vid` 為 v1.8 新模式 |
| 1.4 摘要含品質 | 移除品質 | 同上 |
| 1.5 工具列只留「套用範本」 | 常駐「套用範本」「自己組」兩顆 | 「自己組」（參數化組裝）是 v1.6.9 起的主要入口，收進選單會多一層 |
| 1.6 底部主按鈕 | 加註 `ChatScreen` 不用 | 對話頁輸入列已固定底部 |
| 2.1 worker `stage` | 明確標「目前無此欄位，需新增」並要求兩分支都寫 | `VideoPollWorker.kt` 無 `stage`；v1.8 多了 OpenRouter 輪詢分支 |
| 2.2 廢除「生影」 | 只廢動作文案，導覽短標籤保留 | 「生影」現為三頁 L1 分段與首頁主卡標籤，384dp 三段放長文案會擠（使用者已拍板） |
| 2.2 MediaAction 清單 | 圖片多「存為角色定妝圖」 | v1.7.2 角色資產功能 |
| 2.4 Viewer 圖示改 `visibility_off` | 標「已完成」 | v1.8 已改 |
| 2.4 Snackbar | 加註全 App 尚無 `SnackbarHost`，需先加 | 原始碼 0 處 |
| 3.1 首頁兩張漸層主卡＋四入口 | **重寫**：保留三主卡＋三工具入口，只加「進行中」「最近成果」 | `HubCard` 與漸層已刪，現為 `PrimaryGenCard`×3 + `ToolTile`×3，多了「對話」與「去留審查」入口 |
| 3.3 刪 `IconBtn` 死碼 | **刪除** | 已不存在 |
| 3.5 設定五組 | 對應到現有 10 個區段名稱；註明無 PIN、無品質 | PIN 已移除；「API」現含兩把 key；「帳單 / 後台」含 OpenRouter |
| 3.5 刪重複 `formatApkSize` | 改「只剩一份，改 internal」 | 只找到 1 處 |
| Phase 4 深淺色檢查 | 只檢查深色 | 全 App 強制深色 |
| Phase 4 死碼 | 加 `Routes.VIDEO_GENERATING` | 定義存在但無人導向 |
| 硬約束 | 新增第 8 條 v1.8 約束 | 供應商判斷、模型列、FlowRow、強制深色 |
| 新增原則 6 | `ChatScreen`／`ModelPickerRow` 本輪不動 | 前版未涵蓋，且剛依實機回饋調整 |
| 驗收 | 加 OpenRouter 路徑與第 9 項 | 雙供應商 |
