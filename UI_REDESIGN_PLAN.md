# Imagine App — UI 修改工作清單（v1.8.5 現況對照版）

依據：`UI.md`（含 2026-09-12「v1.7～v1.8.5 增補」章節）＋《Imagine App 介面修改建議》（ChatGPT 評估）＋《給 Claude Code 的最終修改建議》（Claude Design 合併版）。
本版由 Claude Code 對照 **v1.8.5 原始碼** 逐項核實後改寫：目標已不存在或已完成的項目移除或重寫，其餘保留原意。每項標明 **改什麼 → 動哪些檔 → 驗收標準**。
文末「與前版差異」列出所有更動與理由，供再次審閱。
2026-09-12 第二次更新：併入 ChatGPT 第三輪（第六～十六節）→ 新增 Phase 0（丟稿與誤操作）、Phase 5–7；每項附原始碼查證的「現況」。執行順序：Phase 1 → 0 → 2 → 3 → 5 → 6 → 4，Phase 7 隨各批次進行。

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

## Phase 0：避免丟稿與誤操作（第三輪第一批，穿插在 Phase 1–2 之間完成）

以下項目小而獨立，依第三輪建議優先於外觀調整。標「現況」者為 2026-09-12 對照原始碼的查證結果。

### 0.1 提示詞欄位不再獲焦全選 — ✅ 已於 Phase 1.5 完成
### 0.2 草稿保留
- 現況：圖片頁與影片頁的提示詞、參數、來源圖都是 `rememberSaveable`，切頁與系統回收後保留；**主動關閉 App 後不保留**；兩頁草稿各自獨立；生成頁改參數不會寫回設定頁預設（只有模型選擇會寫回 `prefs.imageModel / videoModel`）。
- 做法：新增 `DraftStore`（prefs，圖／影各一份：prompt、模式、參數、來源 uri 字串），生成頁 `LaunchedEffect(prompt, …)` debounce 寫入，進頁時若 saveable 為空則從 DraftStore 還原；「清空」與「開始新作品」分開：前者只清結果，後者清草稿並二次確認。
- 從歷史／教學／素材庫帶 prompt 進來（`initialPrompt`）時若既有草稿非空 → 走 1.5 的「取代／加入末尾／取消」對話框，不再 `LaunchedEffect` 直接覆蓋。
- 驗收：寫到一半切圖片／影片、開素材庫再返回、重啟 App，內容仍在。

### 0.3 防重複送出與「重試」語意分流
- 現況：送出中按鈕已 disabled（圖片頁 `loading`、影片頁 `generating`）；影片頁錯誤卡的「重試」已改「重新生成」（Phase 1）；無「重新查詢」「重新下載」。
- 做法：`VideoPollWorker` 完成但下載失敗（`KEY_ERROR = "完成但下載失敗"`）時錯誤卡改顯示「重新下載」（只重跑下載段，不建新請求）；Worker 被取消／process 死後回來顯示「重新查詢」（用既有 `trackedRequestId` 重新 enqueue 輪詢）；送出後網路中斷且未拿到 requestId 才顯示「重新生成」。
- 驗收：三種失敗各自只出現對應的一顆按鈕；重新下載不產生新扣費。

### 0.4 刪除範圍明確 — 併入 Phase 2.4
### 0.5 多選模式手勢一致
- 現況：`HistoryScreen` 一般長按＝進入多選；**多選中長按＝複製提示詞**；切換篩選不清空選取，但刪除只作用於可見項。
- 做法：多選中長按不再複製（複製移到詳情頁／更多選單）；切換篩選即清空選取並 AppNotice 提示「已清除選取」。

---

## Phase 5：日常效率補充（第三輪第三批）

### 5.1 首頁最近作品與進行中任務 — 併入 Phase 3.1
### 5.2 範本佔位符 — ✅ Phase 1.5 已改「下一個待填欄位（還有 N 處）」；補：送出前若仍含【】佔位符，彈「返回填寫／照原文送出」。
### 5.3 匯入回饋
- 現況：素材庫「從相簿匯入到「分類」」、設定「歷史匯入」、外部分享 `ImportActivity` 三個入口只報成功數；`MediaImporter.importAll` 失敗項靜默跳過。
- 做法：`MediaImporter` 回傳 `ImportResult(ok, failed)`；三處文案改「已匯入 N 個，M 個失敗（格式不支援或讀取被拒）」；無提示詞的外部素材在歷史詳情標「外部匯入・無提示詞」。
### 5.4 長片組合非拖曳排序
- 現況：只有長按拖曳與 ✕ 移除，無上／下移，移除無復原。
- 做法：`TrackCell` 加「更多」選單（往前移／往後移／移除）；移除後 Snackbar 復原（沿用 2.4 的 SnackbarHost）。
### 5.5 提示詞檢查說明
- ✅ Phase 1.5 已把「建議 N/5」改名「提示詞檢查」；補：`PromptAdvisorSheet` 頂部加一句「這是文字完整性檢查，不代表生成品質；簡短提示詞也能直接生成」。本地風險提醒與服務實際拒絕已分開（hint 黃燈 vs `flagged` 紅卡）。
### 5.6 參數同時顯示用途與值 — ✅ Phase 1.4 已用 `aspectLabel()`（直式／橫式／方形）；解析度維持真實規格。

---

## Phase 6：設定、儲存與作品瀏覽（第三輪第四批）

### 6.1 API Key 測試連線
- 現況：`ApiKeyEditScreen` 已有遮蔽／顯示切換與「從剪貼簿貼上」；**儲存只做前綴＋長度檢查，從未打 API**，`verifiedAt` 直接寫今天。
- 做法：儲存前「測試連線」：xAI 打 `GET /v1/models`（或現有最輕端點）、OpenRouter 打 `/credits`；結果顯示在欄位下方，文案分三種：「金鑰格式錯誤」「金鑰無效（401）」「無法連線」；成功才寫 `verifiedAt`。
- 首次使用：現況 Splash 直接進首頁、不強制填金鑰（✅ 已符合）；影片頁補未設 key 提示卡（✅ Phase 1 已加）。
### 6.2 生成前費用預估
- 現況：只有模型列顯示單價（xAI $0.05/張、$0.05/秒；OpenRouter 依目錄）；主按鈕附近**無本次預估**；事後費用只有 OpenRouter 生圖有。
- 做法：`GenerateSettingsSummary` 摘要尾端附「約 $X」＝單價 × 張數／秒數（`ModelCatalog.priceText` 可解析出單價者才顯示，無法取得就不顯示，不推測）。
### 6.3 空間與快取
- 現況：設定頁無容量統計、無清除快取、無「僅 Wi-Fi 下載」。
- 做法：「儲存與資料」分頁顯示 `filesDir/media` 總量與 Coil 磁碟快取大小，提供「清除快取」（不動作品）；下載前 `StatFs` 檢查剩餘空間，不足時 AppNotice「裝置空間不足」；「僅 Wi-Fi 下載」為新增設定，Worker 下載前檢查。
### 6.4 分享與匯出
- 現況：分享 Intent 只帶 `EXTRA_STREAM`，無「附上提示詞」；分享固定第一張；存相簿已支援整批；檔名 `imagine_yyyyMMdd_HHmmss_SSS_<seq>.<ext>`。
- 做法：分享選單加「附上提示詞」（`EXTRA_TEXT`）；多張結果分享改 `ACTION_SEND_MULTIPLE`；匯出檔名改 `Imagine_2026-09-12_1432_圖片.png`（可讀，沿用 MediaSaver 序號避免衝突）；整批匯出完成顯示成功／失敗數。
### 6.5 所有作品的搜尋、篩選、失敗記錄
- 現況：有搜尋框（只比對 prompt）；篩選 全部／圖片／影片／⭐；無排序；**失敗任務不留記錄**（歷史只列實體檔）；搜尋無結果與全空共用同一段文字。
- 做法：篩選加「失敗」與「已加入素材庫」（取代 ⭐，配合 3.2）；排序「最新／最舊」；失敗任務寫入 `FailedJobs`（prompt、參數、原因、時間），列表以文字標示「失敗・原因」，動作「返回修改」帶回當次設定；搜尋無結果文案獨立為「找不到符合條件的作品」＋「清除搜尋與篩選」。
### 6.6 全螢幕播放器
- 現況：`FullscreenVideoPlayer` 用 `PlayerView` 預設控制列（有時間軸與自動隱藏）；無靜音、無循環、無「以此片段繼續」；列表不自動播放（✅ 已符合）。
- 做法：加靜音、循環開關與「延長影片」動作（走 `MediaActionBar`）。
### 6.7 通知直達作品
- 現況：完成通知只開 `MainActivity`，不帶 route；**Android 13+ 未在程式內請求 `POST_NOTIFICATIONS`**（manifest 有宣告），未授權時通知靜默丟失。
- 做法：首次送出影片任務前請求通知權限（拒絕則 AppNotice 說明）；通知 PendingIntent 帶 `history_entry_uri`，點擊直接開 `HistoryDetailScreen`；尚未下載完成則開對應生成頁。

---

## Phase 7：可讀性、無障礙、空白狀態、名詞統一（隨各批次一起做）

- 可點擊區域 ≥ 48dp（縮圖 ✕、全螢幕關閉鈕、TrackCell 移除鈕）；重要標籤不用 11sp；放大字型時參數欄允許換行。
- 狀態不只靠顏色：選中／失敗／完成同時有文字或圖示；純圖示按鈕補 `contentDescription`（`ImagineIcon` 目前一律 null → 加可選參數）。
- 空白狀態：生成頁首次「描述你想製作的畫面，或從範本開始」＋「套用範本」；素材庫空「加入常用角色、場景或風格圖片」＋「匯入參考圖片」；所有作品空「完成生成後，作品會出現在這裡」＋「開始生成」；搜尋無結果與長片無片段見 6.5／3.3。
- Grok 諮詢：入口與頁面標「Grok 網頁諮詢・使用網頁登入」（現況已有「用瀏覽器開啟」與不自動讀剪貼簿 ✅）；「貼上提示詞」回生成頁由使用者主動觸發（3.6）。
- 名詞對照表（App 內文案、設定、說明統一）：所有作品←歷史；素材庫／已加入素材庫←收藏、星號；製作方式←模式；修改圖片←編輯；圖片動起來←生影、動起來；套用提示詞←使用；使用相同設定←套用設定；重新生成／重新查詢／重新下載←重試；提示詞檢查←建議 N/5；下一個待填欄位←跳格；Grok 網頁諮詢←Grok。**導覽短標籤「對話｜生圖｜生影」不在此表內，保留。**

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

補充測試情境（第三輪）：
- S22 Ultra 一般字型與放大字型各跑一次，關鍵文字、主按鈕與系統導覽列互不遮擋。
- 長提示詞開關鍵盤，游標附近內容可見且可送出。
- 影片任務切背景、鎖屏後返回仍對應原請求；圖片編輯（同步路徑）不宣稱可離開頁面。
- 送出前斷線、送出後狀態未知、生成成功但下載失敗，三種情況分別只出現「重新生成／重新查詢／重新下載」。
- 批次選取後切換篩選，選取數量、提示與實際操作範圍一致。
- 範本覆蓋已有草稿、圖影模式互切、從 Grok 帶文字回來，不會默默丟失原內容。
- 未設定金鑰、金鑰無效、金鑰正確但無法連線，三種文案與動作不同。

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
