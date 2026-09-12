# Imagine — UI 設計文件 (現狀 + 重設計參考)

Imagine 是 xAI Imagine API 的 Android 客戶端，以手機側載 APK 形式發佈（主力機型 Galaxy S22 Ultra）。它把「圖片生成（文生圖／圖生圖）」「影片生成（文生影／圖生影／影生影／延長／組合）」「素材庫與歷史」「提示詞教學範本」「長片本機串接」「Grok 諮詢」整合進單一 App；介面全繁體中文、單一裝置使用（無多帳號／無雲端同步）。底層為 Jetpack Compose + Material 3，自建一套 token 化設計系統，生成流程透過 `runGenerate` 與 `VideoPollWorker` 跨 process 背景輪詢完成。

---

## 導覽地圖 (Navigation Map)

App 採三段式骨架 `ImagineScreen`（AppBar → 內容區 weight 1f → BottomNav），底部三個 `NavTab`：

| Tab | 圖示 | 標籤 | 進入畫面 |
| --- | --- | --- | --- |
| `material` | `auto_awesome` | 素材生成 | `MaterialHubScreen`（四卡分流首頁） |
| `long_video` | `movie` | 長片組合 | `LongVideoScreen` |
| `tutorial` | `lightbulb` | 教學範本 | `TutorialScreen` |

**路由與畫面連接（由 Hub 分流）**：

```
BottomNav
├─ 素材生成 → MaterialHubScreen ──┬─ 🖼️ 圖片 → GenerateImageScreen ──┐
│                                 │      ├─ 子模式「圖片編輯」內嵌 EditPane(ImageEdit)
│                                 │      └─ SegmentedTab 切「影片」⇄ GenerateVideoScreen
│                                 ├─ 🎬 影片 → GenerateVideoScreen ──┐
│                                 │      ├─ 文生影 / 圖生影
│                                 │      ├─ 影片延長/編輯 內嵌 EditPane(VideoExtend/VideoEdit)
│                                 │      └─ 組合延長分支（initialExtendBase≠null）
│                                 ├─ 📁 素材庫 → MaterialLibraryScreen
│                                 └─ 💬 Grok 諮詢 → GrokChatScreen（內嵌 grok.com WebView）
├─ 長片組合 → LongVideoScreen（本機 MediaMuxer 串接，免 API）
└─ 教學範本 → TutorialScreen（精選範本 / 課程圖庫）
        ├─ 範本「使用→生成」→ 帶 prompt 回 GenerateImage/VideoScreen
        └─ 課程圖/影「重繪/動起來/延長」→ 帶素材回生成頁

History（從設定「素材」入口 / 各頁結果區進入）
├─ HistoryScreen（搜尋 + filter chips + 多選刪除）
│      └─ HistoryDetailScreen → ActionRow「編輯這張/這段」→ EditScreen（獨立 wrapper）
└─ 全螢幕：FullscreenImageViewer / FullscreenVideoPlayer（共用 Dialog）

齒輪（任一 AppBar trailing）→ SettingsScreen（12 段，含進素材庫/API key/危險區/更新）
```

跨畫面共用元件：`EditPane`（圖片編輯／影片編輯／影片延長三模式本體，被三處掛載）、`FullscreenImageViewer`（素材庫／結果區／詳情頁共用）、`LibraryImagePickerSheet`（圖生影選起始圖）。

---

## 設計系統 (Design System)

Imagine 採用 **Jetpack Compose + Material 3** 自建的 token 化設計系統，所有 token 鏡像自一份 `imagine-tokens.js`（CSS 原型）。主題入口為 `ImagineTheme`（`ui/theme/Theme.kt`），透過 `MaterialTheme` 注入 `colorScheme` / `ImagineTypography` / `ImagineShapes`，並額外用 `CompositionLocalProvider` 提供三個自訂 local：`LocalBudgetColors`、`LocalSpacing`（= `ImagineSpacing`）、`LocalIsDark`。深淺色由 `isSystemInDarkTheme()` 自動切換。

### 色彩 (Colour Palette)

標準 Material 3 baseline 紫色系（`ui/theme/Color.kt`）。亮色與暗色雙 scheme：

**亮色 `LightColorScheme`（關鍵值）**
- primary `#6750A4` / onPrimary `#FFFFFF` / primaryContainer `#EADDFF` / onPrimaryContainer `#21005D`
- secondary `#625B71` / secondaryContainer `#E8DEF8` / onSecondaryContainer `#1D192B`
- tertiary `#7D5260`
- error `#B3261E` / errorContainer `#F9DEDC`
- surface `#FFFBFE` / onSurface `#1D1B20` / surfaceVariant `#E7E0EC` / onSurfaceVariant `#49454F`
- surfaceContainer `#F3EDF7` / surfaceContainerHigh `#ECE6F0`（卡片底色）/ surfaceContainerHighest `#E6E0E9` / surfaceDim `#F2EFF4`
- outline `#79747E` / outlineVariant `#CAC4D0` / scrim `#52000000`

**暗色 `DarkColorScheme`（關鍵值）**
- primary `#D0BCFF` / onPrimary `#381E72` / primaryContainer `#4F378B` / onPrimaryContainer `#EADDFF`
- secondaryContainer `#4A4458` / onSecondaryContainer `#E8DEF8`
- surface `#1C1B1F` / onSurface `#E6E1E5` / surfaceVariant `#49454F`
- surfaceContainer `#211F26` / surfaceContainerHigh `#2B2930`（卡片底色）/ surfaceContainerHighest `#36343B` / surfaceDim `#141218`
- outline `#938F99` / scrim `#80000000`

**預算狀態色 `BudgetColors`**（脫離 M3 語意色的獨立四級色階，透過 `LocalBudgetColors` 取用）：
- 亮：ok `#10B981` / warn `#F59E0B` / high `#EF4444` / over `#991B1B`
- 暗：ok `#34D399` / warn `#FBBF24` / high `#F87171` / over `#DC2626`

**Hub 卡片漸層**（`MaterialHubScreen.kt`，硬編碼 `Brush.linearGradient`，與 scheme 無關，固定深色霓虹調，文字一律白色）：
- 圖片：`#3A2E6E → #23408A`（藍紫）
- 影片：`#0F5E57 → #244A6E`（青藍）
- 素材庫：`#5A2E6E → #8A2360`（紫洋紅）
- Grok 諮詢：`#1F2A37 → #3A4654`（石板灰）

**其他散見硬編碼色**：PromptInput 的 amber hint 用 `#E0A500`；全螢幕看圖/看影背景純黑 `Color.Black`，頂部頁碼徽章 `Black α0.45`，底部動作膠囊 `White α0.18`、動作列底 `Black α0.5`。

### 字型 (Typography)

`ui/theme/Type.kt`。Sans 用 `FontFamily.Default`（裝置系統字，CJK 走 Noto Sans CJK）、Mono 用 `FontFamily.Monospace`（金額/API key 用）。完整 M3 type scale（字級 / 行高 / 字重）：

- displayLarge/Medium/Small：57/45/36sp，W600
- headlineLarge/Medium/Small：32/28/24sp，W600
- titleLarge 22sp W600；titleMedium 16sp W500（ls 0.15）；titleSmall 14sp W500（ls 0.1）
- bodyLarge 16sp / bodyMedium 14sp / bodySmall 12sp，皆 W400
- labelLarge 14sp / labelMedium 12sp / labelSmall 11sp，皆 W500

**Mono 輔助樣式**：`MonoAmount` 18sp W600（金額）、`MonoBody` 14sp W400（API key）。

**實際元件中常以硬編碼 sp 覆寫**（非全走 type scale），常見值：AppBar 標題 22sp/W600/ls −0.01；HubCard 標題 18sp/W800、描述 12sp；按鈕文字 16/14sp W600；chip 13sp W600；SectionHeader 12sp/W600/ls 0.08；輸入框正文 16sp/行高 24sp；ParamPicker label 11sp、值 15sp；BottomNav label 12sp。

### 圓角 (Corner Radii)

`ui/theme/Shape.kt`，`ImagineShapes`：extraSmall 4dp / small 8dp / medium 12dp（TextField、ParamPicker）/ large 16dp（Card）/ extraLarge 28dp（Dialog、BottomSheet）。

自訂 `ImagineCustomShapes`：PillButton 24dp、Chip 18dp、Media 12dp（圖/影預覽）。

實務硬編碼半徑：PrimaryButton 28dp、ImagineCard 16dp、HubCard 18dp、ImagineChip 18dp、PromptToolChip / 卡片內小膠囊 16dp、SegmentedTab 100dp（全圓 pill）、BottomNav active 指示丸 16dp、看圖動作膠囊 22dp。

### 間距節奏 (Spacing Rhythm)

8dp 網格，`ImagineSpacing`（`Int` dp 值）：xxs 4 / xs 8 / sm 12 / md 16 / lg 24 / xl 32 / xxl 48。畫面內容區慣用 16dp padding 與 16dp 區塊間距（`Arrangement.spacedBy(16.dp)`）；卡片預設內距 16dp（`ImagineCard.pad`）。

### 畫面骨架 (Screen Scaffold)

`ImagineScreen`（`ScreenFrame.kt`）為所有畫面共用容器，三段直排：**AppBar → 內容區（weight 1f）→ BottomNav**。

- 整體 `fillMaxSize` + `surface` 背景；內容區可另傳 `contentBackground`（預設 surface）。
- `appBar` / `bottomNav` 皆為可空 slot（傳 `null` 即隱藏），預設分別是 `ImagineTopAppBar()` 與 `ImagineBottomNav()`。
- **Edge-to-edge**：`MainActivity` 呼叫 `enableEdgeToEdge()`（`decorFitsSystemWindows = false`），故 manifest 的 `adjustResize` 失效，inset 全由 Compose 自行吸收。
- **imePadding**：內容區在 `verticalScroll` 之前套 `.imePadding()`，鍵盤彈出時上推內容、保證 PromptInput 的 `bringIntoView` 能把輸入框帶到鍵盤上方。
- **scroll 旗標**：預設 `scroll = true`（內容包 `Column.verticalScroll`）；內層若放任何 `Lazy*` 元件必須傳 `scroll = false`，否則巢狀垂直捲動會丟 `IllegalStateException`（infinity max height）。`scrollState` 對外暴露，供 caller 在新結果到達時 `animateScrollTo` 把結果捲入視野。
- `showBalanceBar` 為相容保留參數，已不再 render 任何東西。

**TopAppBar**（`AppBar.kt`）：扁平 64dp 高、surface 底、`padding(start 12, end 4)`。左：`showBack` 時為 `arrow_back` IconButton、否則 8dp 佔位；中：標題 22sp/W600（`weight 1f`）；右：`trailing` slot，預設為 `settings` IconButton。

**BottomNav**（`BottomNav.kt`）：`surfaceContainer` 底 + `navigationBars` inset padding（edge-to-edge 下吃手勢列）。列高 80dp、top padding 12dp、三 tab `SpaceAround`。每 tab：上方 56×32dp 圓角丸（active 時填 `secondaryContainer`）內含 22dp 圖示（active `fill=1` 且 tint onSecondaryContainer，否則 outlined + onSurfaceVariant），下方 12sp label（active W600/onSurface，否則 W500/onSurfaceVariant）。

**Bottom Nav tabs（`NavTab` enum）**：
1. `material` — 圖示 `auto_awesome`，標籤「素材生成」
2. `long_video` — 圖示 `movie`，標籤「長片組合」
3. `tutorial` — 圖示 `lightbulb`，標籤「教學範本」

### 圖示集 (Icon Set)

`Icons.kt` 把 Material Symbols 名稱（JSX 原型沿用）映射到 Compose `ImageVector`，`fill=1`→實心、`0`→線框（部分名稱兩態相同）。`ImagineIcon`（預設 24dp）與 `ImagineIconButton`（40dp 觸控盒、圓形、透明底，內含 24dp 圖示）為兩個取用元件。已註冊名稱：

`settings`、`arrow_back`、`add`、`add_photo_alternate`、`close`、`auto_awesome`、`edit`、`history`、`check`、`expand_more`、`expand_less`、`play_arrow`、`fingerprint`、`lock`、`key`（→ VpnKey）、`image`、`lightbulb`、`movie`、`download`、`share`、`content_copy`、`content_paste`、`visibility`、`warning`、`refresh`、`search`、`language`、`star`（線框 StarBorder / 實心 Star）。未知名稱 fallback 為 `Icons.Outlined.AutoAwesome`。

### 可重用元件 (Reusable Components)

**`ImagineCard`**（`Cards.kt`）— 通用內容卡。`fillMaxWidth`，shape 16dp，內距預設 16dp（`pad`）。`CardVariant.Filled`（底 `surfaceContainerHigh`）或 `Outlined`（透明底 + 1dp `outlineVariant` 邊框）。`onClick` 可選使整卡可點。

**`SectionHeader`**（`Cards.kt`）— 卡片群之間的小節標題。12sp/W600/ls 0.08、`onSurfaceVariant` 色，左右/上下各 4dp 內距。

**`PrimaryButton` / `OutlinedActionButton` / `TextActionButton`**（`Buttons.kt`）：
- *Primary*：滿寬膠囊，56dp 高、半徑 28dp、primary 底/onPrimary 字、文字 16sp/W600。`loading` 時顯示 22dp 白色 `CircularProgressIndicator`（stroke 2.5dp）並停用；`disabled` 時底色 α0.5、字 α0.7；`icon` 可選（20dp 實心）。
- *Outlined*：48dp 高、半徑 24dp、透明底 + 1dp `outline` 邊框、primary 字 14sp/W600、icon 18dp。
- *Text*：40dp 高、半徑 20dp 膠囊、透明、預設 primary 字（可傳 `color` 覆寫）、14sp/W600、icon 18dp。

**`ImagineChip`**（`Chip.kt`）— 36dp 高、半徑 18dp、水平內距 14dp。`ChipVariant.Tonal`（底 `primaryContainer` / 字 `onPrimaryContainer`）或 `Outlined`（透明 + 1dp `outline` 邊框 / `onSurface` 字）。文字 13sp/W600，icon 16dp，icon↔text 間距 6dp，`onClick` 可選。

**`SegmentedTab`**（`SegmentedTab.kt`）— 分段切換控制。滿寬 40dp 高、半徑 100dp 全圓 pill、1dp `outline` 外框，段與段之間以 1dp 直線分隔。各段 `weight 1f` 均分；選中段背景預設 `secondaryContainer`（可傳 `activeColor` 改用模式色 + 白字白 icon），選中時左側顯示 16dp `check` 圖示（與文字間距 6dp），文字 14sp（active W600 / 否則 W500）。資料模型 `SegmentedOption(id, label)`。

**`ParamPicker`**（`ParamPicker.kt`）— 參數選擇欄，點擊由底部彈出 `ModalBottomSheet`（`skipPartiallyExpanded`，底色 `surfaceContainerHigh`）選一項。欄本體：滿寬、`heightIn(min=64dp)`、半徑 12dp、1dp `outline` 邊框、surface 底，內距 h12/v10。上排 label 11sp/W500/onSurfaceVariant，下排為當前值 15sp/W500（單行省略）+ 右側 20dp `expand_more`。Sheet 內每項 h20/v14，選中項 primary 色 + W600 + 實心 `check`。`displayName` 可做值→顯示文字轉換。

**`PromptInput`**（`PromptInput.kt`）— 核心提示詞輸入框，功能密集。
- 結構：上方工具列（左 label 13sp/W600、右側 `PromptToolChip` 小膠囊群）+ 多行 `BasicTextField` 容器。
- 容器：`heightIn(min=156dp)`（給三行可見 + S22U 拇指區）、半徑 12dp、surface 底，內距 start/end 16、top 14、bottom 32。正文 16sp/行高 24sp，游標 primary 色。
- **狀態邊框**：normal 1dp `outline`；focused 2dp `primary`；`flagged`（被審核擋下）2dp `error`；label 與 hint 同步變色。
- **PromptToolChip**：半徑 16dp、`surfaceVariant` 底、primary 字 12sp/W600、15dp 實心 icon。內容為空時顯示「套用範本 / 自己組」，有內容時顯示「複製 / 建議 N/5」（5 要素涵蓋計數），含 `【】` 佔位符時加「跳格」。
- **行為**：`bringIntoViewRequester` 配合 imePadding 抬升輸入框；獲焦自動全選現有內容（套範本/插片語後以 `suppressSelectAllOnce` 略過）；範本/片語/智慧插入/跳格/風險替換等編輯流程；右下角顯示本地 heuristic 審核風險 hint（紅/amber `#E0A500`/灰，僅參考）。另導出 `ConfirmHighRiskDialog`（送出前軟確認）與 `scanPromptRisks` / `scanQualityHits` 等掃描函式。

**`FullscreenImageViewer`**（`FullscreenImageViewer.kt`）— 全螢幕看圖 `Dialog`（`usePlatformDefaultWidth=false`、`decorFitsSystemWindows=false`），純黑底。`HorizontalPager` 多張左右滑（縮放中自動停滑），Coil `AsyncImage` + `ContentScale.Fit`。`ZoomableImage` 支援雙指縮放（1–5x）、雙擊 1x↔2.5x、拖移平移。頂部：`close` 白色 IconButton + 頁碼徽章（`Black α0.45`、small shape）。底部：`ViewerAction` 動作膠囊列（`Black α0.5` 底、可水平捲動、半徑 22dp `White α0.18` 膠囊、icon 20dp + 14sp/W600 白字），底距由真實導覽列 inset + 40dp、保底 80dp 推高避免被三鍵列遮住。

**`FullscreenVideoPlayer`**（`FullscreenVideoPlayer.kt`）— 全螢幕影片 `Dialog`（同上 edge-to-edge 設定），純黑底。Media3 `ExoPlayer` + `PlayerView`（`useController=true`、`RESIZE_MODE_FIT` 按真實比例不變形、`playWhenReady=true` 自動播），關閉即 `player.release()`。左上 `close` 白色 IconButton（`statusBarsPadding`）。

**`HubCard`**（`MaterialHubScreen.kt`，private）— 首頁大型入口卡。滿寬、`heightIn(min=156dp)`、半徑 18dp、`Brush.linearGradient` 漸層底（見上）、內距 20dp、置中直排。內容：emoji 34sp、標題 18sp/W800 白字、描述 12sp 白字 α0.85。`MaterialHubScreen` 以四張 HubCard（圖片 / 影片 / 素材庫 / Grok 諮詢）構成「素材生成」首頁，外層即 `ImagineScreen`（active tab = MATERIAL）。

---

## 首頁與生成 (Home & Generation)

本節涵蓋四個畫面：素材生成首頁 (`MaterialHubScreen`)、生成圖片頁 (`GenerateImageScreen`)、生成影片頁 (`GenerateVideoScreen`)、以及共用的編輯本體 (`EditPane` / `EditScreen`)。所有畫面共用 `ImagineScreen` 外殼（頂部 `ImagineTopAppBar` 標題「Imagine」+ 設定齒輪、底部 `ImagineBottomNav`，底部分頁恆為 `NavTab.MATERIAL`），主內容皆為 `Column` 包 `padding(16dp)` + `spacedBy(16dp)` 的垂直節奏。

### 1. 素材生成首頁 `MaterialHubScreen`

**用途**：App 的入口分流頁，用四張大卡把使用者導向圖片 / 影片 / 素材庫 / Grok 諮詢。

**由上而下的版面**：
1. TopAppBar：「Imagine」+ 設定齒輪。
2. `SectionHeader("要產什麼素材？")`。
3. 四張 `HubCard`（皆 `heightIn(min = 156dp)`、圓角 18dp、線性漸層背景、置中 emoji + 標題 + 副標）：
   - 🖼️ **圖片** — 「文生圖 · 圖生圖」(紫藍漸層)
   - 🎬 **影片** — 「文生影 · 圖生影 · 影生影 · 延長」(青綠漸層)
   - 📁 **素材庫** — 「角色 · 環境 · 物件 · 風格 參考圖庫」(紫桃漸層)
   - 💬 **提示詞諮詢 · Grok** — 開 grok.com 網頁版 (深灰漸層)

**關鍵互動**：整張卡 `clickable`，分別觸發 `onPickImage` / `onPickVideo` / `onOpenLibrary` / `onOpenGrok`。

**問題**：四張卡同尺寸同權重，純靠 emoji + 漸層配色區分，無圖示體系也無視覺優先級——主要動作（生成圖/影）與次要動作（素材庫/Grok）扁平並列。卡片副標把模式名稱塞成一行點分隔字串（如「文生影 · 圖生影 · 影生影 · 延長」），在窄機上易擠壓。emoji 與標題、副標三段 padding 硬寫 6dp/2dp，節奏偏緊。

### 2. 生成圖片頁 `GenerateImageScreen`

**用途**：文生圖 / 圖生圖的主生成頁，並內嵌「圖片編輯」子模式。

**由上而下的版面**：
1. **模式色彩標頭**：藍色 (`0xFF23408A`) 圓角彩條 + 「🖼 圖片模式」白字——用來一眼分辨目前在圖片還是影片頁。
2. **頂層 SegmentedTab（圖片 / 影片）**：選「影片」即 `onSwitchToVideo()` 跳頁。
3. **子模式 SegmentedTab（生圖 / 圖片編輯）**：切到「圖片編輯」時直接內嵌 `EditPane(ImageEdit)` 並 `return@Column`（其餘生圖 UI 完全不渲染）。
4. **API Key 未設提示卡**（`!prefs.isApiKeySet` 時）：紅字卡，點擊跳設定。
5. **品質 SegmentedTab（Rapid 快速 / Quality 高品質）**：對應 `grok-imagine-image` / `grok-imagine-image-quality` 兩個 model。
6. **`PromptInput`**：提示詞輸入，被審核擋下時 `flagged = true` 紅框警示。
7. **參數列（單一 Row 三欄等寬）**：解析度（1k/2k）、長寬比（16:9/1:1/9:16/4:3/3:4/3:2/2:3/auto）、數量（1–10）。此頁**無秒數、無影片參數**。
8. **`PrimaryButton`「生 成」**：loading 時顯示「生成中…」+ spinner；送出前若 `firstHighRiskTerm` 命中高風險詞，先彈 `ConfirmHighRiskDialog`。
9. **錯誤卡**（`lastError` 非空）：審核被拒 (ContentPolicy) 用紅色 `errorContainer`，一般錯誤用 `surfaceContainerHigh`；右側帶「清除」chip。
10. **結果區**（`resultUrls` 非空）：「上次結果」小標 → `ImagineCard` 內垂直堆疊所有結果圖（點圖開 `FullscreenImageViewer`）→ 可長按複製的 prompt（`SelectionContainer`）→ 等寬 mono meta（長寬比·解析度·張數）→ 動作 `FlowRow`（複製 prompt / 存到相簿 / 分享 / 編輯 / 動起來）→「設為素材庫(整批)」小標 + 分類 chip 群。

**模式**：生圖（文生圖）/ 圖片編輯（內嵌 EditPane）。圖生圖能力在此頁未直接暴露——實際走影片頁的起始圖或編輯頁。

**關鍵互動**：點「生成」自動收鍵盤並清上一輪錯誤；成功後背景下載存相簿（用 `appScope` 避免切走被砍）、`pendingScrollToResult` 自動捲到結果區、`resultGen++` 換 Coil 快取 key 避免看到舊圖。

**問題**：頁面頂部**連續堆了三個橫條控制元件**（色彩標頭 + 圖片/影片 SegmentedTab + 生圖/編輯 SegmentedTab），再加品質 SegmentedTab 共四條，未生成前畫面上半部全是分段控制，視覺擁擠且層級不清。色彩標頭與下方 SegmentedTab 的藍色重複但形狀不一致。結果區兩組 chip（動作列 + 素材庫分類）連續排列僅靠小標題分隔，密度偏高。

### 3. 生成影片頁 `GenerateVideoScreen`

**用途**：文生影 / 圖生影 / 影片延長 / 影片編輯 / 組合延長 的總集頁，內含多個子流程。

**由上而下的版面（一般流程，`initialExtendBase == null`）**：
1. **模式色彩標頭**：青綠 (`0xFF0F5E57`) 圓角彩條 + 「🎬 影片模式」。
2. **頂層 SegmentedTab（圖片 / 影片）**：選「圖片」即 `onSwitchToImage()`。
3. **模式區（`SectionHeader("模式")` + 兩排 SegmentedTab）**：因手機寬度容不下 4 段而**刻意拆成兩排各 2 段**——第一排「文生影 / 圖生影」(生成組)、第二排「影片延長 / 影片編輯」(編輯組)。兩排只有一排會高亮，另一排 `activeId=""` 不匹配故無高亮，藉此暗示目前不在那組。
4. **影片延長 / 影片編輯** → 內嵌 `EditPane(VideoExtend / VideoEdit)` 並 `return@Column`。
5. **起始圖區**（`mode != T2V`，即圖生影時）：`SectionHeader("起始圖")` + 已選圖 `SelectedImageSlot`（80dp 帶移除 X）+ `AddImageSlot`（點開素材庫 picker sheet）。最多 1 張（圖生影）/ 3 張（其它）。下方常駐「複製 prompt」「下載原圖」chip。
6. **`PromptInput`**：placeholder「描述要怎麼動...」、`forVideo = true`、圖生影時 `videoHasImage = true`。
7. **參數列（單一 Row 三欄等寬）**：秒數（1–15，顯示「N 秒」）、長寬比（16:9/1:1/9:16/4:3/3:4/3:2/2:3，**無 auto**）、解析度（480p/720p）。**無數量、無品質**選項。
8. **生成中卡 / 生成按鈕（互斥）**：生成中顯示 `ImagineCard` 內 48dp `CircularProgressIndicator` + 「影片生成中…」+ mono 計時器 (`m:ss`) + 依秒數粗估的 `LinearProgressIndicator`（封頂 97%）+「可切背景/鎖屏，完成發通知」+ 紅字警告「請勿從最近應用滑掉」。否則顯示「生 成」`PrimaryButton`。
9. **錯誤卡**：同圖片頁雙色策略，但多一顆「重試」chip。
10. **結果區**（`resultVideoUrl` 非空）：「上次結果」→ `ImagineCard` 內 `VideoPreview`（ExoPlayer，`heightIn 280–480dp`）→ 可複製 prompt → 右對齊 `TextActionButton`（複製 / 存到相簿 / 分享）。

**素材庫 picker sheet（`LibraryImagePickerSheet`）**：`ModalBottomSheet`，頂部「從手機相簿選」OutlinedActionButton（走系統 PhotoPicker）+ 3 欄 `LazyVerticalGrid` 縮圖（只列非影片）+ 空狀態文案。

**組合延長專屬精簡流程（`initialExtendBase != null`）**：標題列改「🔗 組合延長」，走另一條 `return@Column` 分支——青綠標頭 + 說明卡（「自動把原片+續集接成長片」）+「尾格（續接起點）」`SelectedImageSlot`（不可刪）+ PromptInput + 單一全寬秒數 ParamPicker +「解析度自動沿用原片」說明 + 生成中卡 +「生成續集 → 自動接成長片」按鈕 + 成功提示「去歷史找組合延長」+ VideoPreview。

**問題**：**「模式」用兩排各 2 段的 SegmentedTab 是明顯妥協**——四個並列功能被人為切成兩組，靠「只有一排高亮」的隱性規則表達狀態，使用者難一眼理解這是 4 選 1。生成、編輯、延長三類異質功能擠在同頁靠 `return@Column` 早退切換，入口外觀相似但渲染差異極大。生成中卡塞了 spinner + 計時器 + 進度條 + 兩段說明 + 紅字警告共五層資訊，偏冗長。組合延長走完全獨立 UI 分支，與主流程重複大量結構卻不共用。參數列三欄在窄機上 ParamPicker 標籤易被擠。

### 4. 編輯本體 `EditPane` / `EditScreen`

**用途**：圖片編輯 / 影片編輯 / 影片延長三種「來源 → 編輯說明 → 執行 → 結果」流程的共用本體。`EditPane` 不含 Scaffold，可被生成頁內嵌；`EditScreen` 是給 History「編輯這張/這段」入口用的獨立 wrapper（多包 AppBar/BottomNav + 三段模式 SegmentedTab）。兩者同檔（`EditScreen.kt`，無獨立 `EditPane.kt`）。

**由上而下的版面（EditPane）**：
1. **來源區**：`SectionHeader("來源")` + 200dp 高點擊框——未選顯示圓形 `add_photo_alternate` + 「選擇圖片/影片」「點此從相簿選取」；已選顯示 AsyncImage（圖）或 `VideoThumb`（影，ExoPlayer 首格）+ 右上角圓形移除鈕。
2. **編輯說明區**：`SectionHeader("編輯說明")` + `PromptInput`，placeholder 依 mode 變（「把背景換成夕陽…」/「把場景換成下雨夜晚…」/「讓主角繼續往街道走…」），影片模式 `forVideo = true`。
3. **執行中卡 / 執行按鈕（互斥）**：執行中顯示 40dp spinner + 「圖片/影片編輯中…」「影片延長中…」，影片模式多一個 mono 計時器（圖片編輯無計時器）。否則顯示「執 行」`PrimaryButton`。高風險詞同樣先彈確認。
4. **結果區**：「結果」小標 + `ImagineCard`——圖片結果為 AsyncImage（點開全螢幕）、影片結果為 `EditVideoPreview`（`heightIn 200–460dp`，帶全螢幕鈕）；下方 `ResultActionRow`（複製 / 存到相簿 / 分享，右對齊 TextActionButton）。

**模式**：`EditMode.ImageEdit` / `VideoEdit` / `VideoExtend`，由外部參數控制。`EditScreen` 在頂部提供三段 SegmentedTab。圖↔影越界切換時清空來源與結果，影片編輯↔延長互切則保留來源。

**關鍵互動**：圖片編輯走 scope-cancel 同步路徑（`editImage`）；影片編輯/延長走 `VideoPollWorker` 背景路徑（`trackedRequestId` 為 SSOT，可跨 process 恢復）。

**問題**：同一 `EditPane` 被三處掛載，模式 SegmentedTab 的位置與外觀**三處不一致**（獨立頁三段一排、圖片頁二段、影片頁兩排各二段）。EditPane 刻意**不自帶 padding**，依賴每個 caller 外層提供 16dp，是易出錯的隱性約定。圖片/影片編輯的執行中卡資訊量不對稱。「來源」固定 200dp 框與「結果」自適應卡片在直式長圖/橫式影片下比例不協調。

### 跨畫面的一致性觀察

- **分段控制濫用**：SegmentedTab 同時承擔「頂層導航（圖片↔影片）」「子模式（生圖/編輯）」「功能選擇（文生影/圖生影/延長/編輯）」三種語意層級，外觀卻幾乎相同。影片頁「模式」更被迫拆成兩排表達 4 選 1。
- **模式色彩標頭**（藍/青綠彩條）與其下方同色 SegmentedTab 資訊重複，且只在生成頁有、編輯頁無。
- **參數選項在兩頁不對齊**：圖片頁有解析度/長寬比/數量/品質、影片頁有秒數/長寬比/解析度，長寬比選項集也不同（圖片有 auto、影片無）。
- **生成中狀態三套實作**（圖片頁無生成中卡只換按鈕字、影片頁與組合延長各一張詳細卡、EditPane 一張簡卡），視覺與資訊量不統一。
- **結果區動作鈕兩種元件**：圖片頁用 `ImagineChip`（FlowRow），影片頁與 EditPane 用 `TextActionButton`（右對齊 Row），同樣是「複製/存相簿/分享」卻兩種視覺語言。

相關檔案：
- `...\ui\hub\MaterialHubScreen.kt`
- `...\ui\generate\GenerateImageScreen.kt`
- `...\ui\generate\GenerateVideoScreen.kt`
- `...\ui\edit\EditScreen.kt`（同檔定義 `EditPane` 與 `EditScreen`）

---

## 素材庫與歷史 (Library & History)

### 素材庫 (MaterialLibraryScreen)

素材庫以「角色／環境／物件／風格」四個分類分頁（`MaterialLibrary.CATEGORIES`），用頂部 `SegmentedTab` 切換，每個 tab 標籤帶即時計數（例如「角色 12」），計數涵蓋使用者自有素材與內建素材。

每個分頁內容由上而下：
- **從相簿匯入列**：`secondaryContainer` 底色橫幅按鈕（圖示 + 「從相簿匯入到『角色』」），一次最多 20 張，匯入後自動歸到目前分類。
- **說明文字**：一行灰字（點圖放大、雙指縮放、可當圖生圖／圖生影、內建素材由課程範例圖自動分類）。
- **3 欄方形網格（`GridCells.Fixed(3)`，正方裁切）**，兩分區各帶跨整列的 `SectionLabel`：
  - **「我的素材 N」**：使用者自己生成或匯入的圖片（僅圖片，影片被排除）。
  - **「內建課程素材 N」**：由課程範例圖視覺分類而來的 CDN 圖（seed），僅供取用，不可移除或改分類。
- **空狀態**：該分類無素材時置中顯示「『角色』還沒有素材…」。

**全螢幕檢視 + 動作膠囊**：點任一格進共用 `FullscreenImageViewer`。底部一排膠囊（icon + 文字），作用在「當前頁」那張圖：
- 我的素材 **5 顆**：生圖 / 生影 / 存相簿 / 改分類 / 移出素材庫。
- 內建素材 **3 顆**：生圖 / 生影 / 存相簿。
- 「改分類」彈 `Dialog` 列四分類；「移出素材庫」先彈 `AlertDialog` 確認（只移出，不刪圖檔）。

### 歷史 (HistoryScreen)

頂部由上而下：
- **prompt 搜尋框**：`OutlinedTextField`，placeholder「搜尋 prompt 找圖／影片」，即時對 `entry.prompt` 不分大小寫 `contains` 過濾。
- **篩選 chips（`SegmentedTab`）**：全部 / 圖片 / 影片 / ⭐（已標進素材庫者），各帶計數。
- **App Bar trailing**：「選取／完成」文字鈕，進入／離開多選刪除模式；多選時標題變「已選 N」。

**3 欄網格**：項目先以日期分組（`groupBy formatDate`），每個日期一條跨列等寬字體小標題。縮圖 (`HistoryThumbnail`) 疊加：
- 影片用首格縮圖（`MediaMetadataRetriever` 解第一格），疊中央播放鍵與右下時長標籤。
- 已歸素材庫者左上角 ⭐ 角標；有 prompt 時底部疊兩行省略 prompt 字幕。
- 點擊開詳情；**長按**進入多選並選取該張（多選下長按則複製 prompt）。

**多選刪除模式**：縮圖暗化 + 右上勾選圈；有可見選取項時網格上方出現紅色「🗑 刪除已選 N 項」橫幅，點擊彈 `AlertDialog` 確認（永久刪檔）。選取以「目前可見項目」交集計算，避免切 filter／搜尋後誤刪看不見的檔。

### 歷史詳情 (HistoryDetailScreen)

由上而下，區塊間距 20dp，整體 `navigationBarsPadding` 上推避開系統導覽列：
- **媒體預覽**：影片用 ExoPlayer `PlayerView`（含控制列與全螢幕鈕）；圖片用 `AsyncImage`，點圖開 `FullscreenImageViewer`（動作只有 存相簿 / 分享）。
- **Prompt 卡片**：`SelectionContainer` 可長按選取；下方右對齊兩文字鈕「複製」「使用」（使用 = 把 prompt 帶回文生圖頁）。舊檔（v1.0.24 前）無 prompt 時顯示灰字佔位並隱藏按鈕。
- **詳細資訊卡片**：`DetailRow` 列出 檔名 / 類型 /（影片才有）時長 / 建立時間，行間 0.5dp 分隔線，檔名與時間用等寬字體。
- **分類選擇列（`CategoryPickerRow`，僅圖片）**：水平捲動圓角 chip（四分類），點選即設為該分類、再點同一個取消（影片隱藏）。
- **動作列（`ActionRow`）**：編輯這張／動起來（生影片）（影片版為「編輯這段／延長影片」）、存到相簿、分享，每列 icon + 文字並以 `HDivider` 分隔。

相關檔案：
- `...\ui\hub\MaterialLibraryScreen.kt`
- `...\ui\history\HistoryScreen.kt`
- `...\ui\history\HistoryDetailScreen.kt`
- `...\ui\component\FullscreenImageViewer.kt`

---

## 教學・長片・諮詢・設定 (Tutorial, Long-video, Grok, Settings)

> 四個畫面均為單檔 Jetpack Compose（Material3），共用 `ImagineScreen` 外殼。配色全走 `MaterialTheme.colorScheme` token，無硬編色（少數例外：影片縮圖黑色半透明遮罩、白字 overlay）。重設計時這套外殼 + token 結構是可沿用的骨架。

### 1. TutorialScreen（教學範本，底部第 3 分頁）

`ui/tutorial/TutorialScreen.kt`

**版面（固定不滾動頂部 + 內部 LazyColumn）**
- 頂部 `ImagineTopAppBar`（標題「教學範本」，齒輪進設定）。
- 搜尋框 `OutlinedTextField`（leading search icon，placeholder「搜尋範本 / 課程 / 提示詞」），同字串同時過濾兩分頁。
- 一級 `SegmentedTab`：**精選範本(ready) / 課程圖庫(gallery)**。
- 一行 11sp 灰字操作提示。
- 內容區依分頁切 `ReadyList` 或 `GalleryList`（各 `weight(1f)`）。

**精選範本（ReadyList）**
- 二級 `SegmentedTab` 模式切換：**文生圖 t2i / 文生影 t2v / 圖生影 i2v**，三者完全分開；切模式把分類重置回「全部」。
- 動態分類 chips（水平捲動，自製 `CatChip` 圓角 pill，選中走 `secondaryContainer`）：固定前綴「全部」「★ 收藏」，接著依當前模式實際存在的範本動態產生分類；「古裝/現代」若存在被提到最前。分類來源 `categoryOf()` 依 tag 歸入 PEOPLE_TAGS / SCENE_TAGS / 影片 / 主題，純展示層歸類。
- 卡片列表 `ReadyPromptCard`（資料源 `READY_PROMPTS`，以 `usageOf(ex)==mode` 過濾）：每卡「複製」「使用→生成」（`onUsePrompt(text, usage)`，i2v 會讓影片頁進圖生影）、★ 收藏 toggle（存 `prefs.favoriteTemplates`）。
- 空狀態：「沒有符合的範本，換個關鍵字或分類試試。」

**課程圖庫（GalleryList）**
- 資料 `TutorialData.load(ctx)`，依節次 `sec` 由新到舊排；載入失敗顯示「課程資料載入失敗」。
- 每課一張可摺疊 `LessonCard`：標頭「第 N 節」+ 標題 + 統計「X 圖 · Y 影片 · Z 提示詞」+ expand chevron。
- 展開後三區：
  - **範例圖**：水平捲動 150dp 縮圖；點圖開 `FullscreenImageViewer`，底部兩動作 **重繪 / 動起來**（→ `onUseImage(url, asVideo)`）。
  - **示範影片**：未播時一列「播放範例影片」；點開內嵌 `InlineVideoPlayer`(220dp) + 兩 `ActionRow`：**影片修改（影生影）/ 影片延長（接續）** → `onUseVideo(url, "video"|"extend")`。一次只播一支。
  - **原始提示詞片段**：每段卡片含「複製 / 使用(生圖) / 使用(生影，僅影片課顯示)」。

**粗糙點**：`ActionRow` 與 `LessonCard` 內樣式各自硬寫 padding/shape（與 `ReadyPromptCard`、`SettingRow` 不共用容器）；`CatChip` 也是自製而非共用 chip。分類歸類靠 tag 字串集合硬編，新範本 tag 不在集合內會掉進「主題」。

### 2. LongVideoScreen（長片組合，CapCut/剪映風）

`ui/longvideo/LongVideoScreen.kt`。純本機 `MediaMuxer`/`VideoMerger` 串接，**不花 API**。整頁單一可滾動 `Column`。

**版面（由上而下）**
- 標頭 `SectionHeader("長片組合")` + 說明文字（點縮圖預覽、片段需同解析度/編碼才能直接串接）。
- 固定的「**銜接技巧**」灰底資訊卡（`surfaceVariant`），四條銜接法（拆段 / 影片延續影片 / 重疊銜接 / 藏卡頓）——純靜態教學文字。
- **① 主組裝條**（`AssemblyTrack`，剪映風時間軸）：水平捲動已選片段；每格 96×72dp `TrackCell`，左上序號徽章、右上 ✕ 移除、縮圖取首格。互動：**點縮圖預覽播放、長按拖曳重排**（`detectDragGesturesAfterLongPress`，超過半格寬就與相鄰交換）。空狀態為灰底提示框。下方「總長 X」+「▶ 預覽組裝」pill（`AssemblyPreviewDialog`：ExoPlayer playlist 依序播放，免合成即可預覽）。
- **合成按鈕**：需 ≥2 段才 enable；合成中顯示 spinner +「合成中…」。成功 toast + 跳預覽 + 清空組裝條 + reload；失敗 toast「片段需同解析度/編碼才能直接串接」。合成結果以 prompt 前綴 `長片組合` 標記。
- **② 可用片段**：`SegmentedTab` 三種整理 **相似 / 最新 / 時長**。「相似」用 `similarityKey()` 分群顯示群組小標。每列 `AvailRow`：首格縮圖（點播放）+ 標籤/時長 + 複製 prompt icon + **movie icon（擷取某格→圖生影 / 存為角色，開 `VideoFramePicker`）** + 「加入」pill。
- **③ 已合成的長片**：可摺疊歷史區，可再「加入」回組裝條繼續接龍。

**Dialog**：`VideoPreviewDialog`（全寬 ExoPlayer + 存到相簿 / 分享）、`AssemblyPreviewDialog`（全螢幕 playlist 預覽）、`VideoFramePicker`（畫格擷取器，回呼圖生影 / 組合延長）。所有 ExoPlayer 均 `DisposableEffect` 關閉即 release。

**粗糙點**：標題與 SectionHeader「長片組合」重複出現兩次。三層編號區段（①②③）用全形數字硬編在 Text 內，非結構化。存在未使用的 private `IconBtn` composable（疑似死碼，可移除）。「相似」分群鍵是 prompt 啟發式，prompt 為空全部落到「未命名」。縮圖解碼每次重建（無快取），長列表反覆抽幀。

### 3. GrokChatScreen（提示詞諮詢 · Grok）

`ui/grok/GrokChatScreen.kt`。**不是 API 對接，而是內嵌 `grok.com` 網頁版 WebView，用使用者帳號登入。** 此畫面是「諮詢 / 寫 prompt」外部入口，與 App 內生成流程脫鉤（要把結果帶回需走「分享回 Imagine」或手動複製）。

**版面**：`ImagineScreen` 外殼，無底部導覽、不滾動。AppBar 標題「提示詞諮詢 · Grok」，左上返回（WebView 能 `goBack` 就先退頁，否則離開，`BackHandler` 同邏輯），右上兩鈕：**refresh（reload）** + **language 🌐（逃生口：用系統瀏覽器開 grok.com）**。內容區全屏 `AndroidView(WebView)`，載入中頂部 `LinearProgressIndicator`（progress 1–99 才顯示）。

**WebView 設定 / 互動**
- JavaScript、DOM storage、database、多視窗、`mediaPlaybackRequiresUserGesture=false` 全開；偽裝成真實 Chrome UA（降低 Google/X OAuth 把內嵌 WebView 擋成「不安全瀏覽器」）。
- Cookie 持久化（`setAcceptThirdPartyCookies` + `onDispose` 時 `CookieManager.flush()`），登入狀態跨次保留。
- `shouldOverrideUrlLoading` 一律回 false；`onCreateWindow` 攔 OAuth 彈窗（`window.open`/`target=_blank`）導回主 WebView 載入。

**粗糙點**：登入靠帳號而非 API，本質脆弱——OAuth 供應商隨時可能擋內嵌 WebView，故需 🌐 逃生口。UA 字串硬編某機型（SM-S908B / Chrome 126），會過時。結果回流路徑依賴系統分享，非無縫整合。整頁無 App 風格 UI，與其餘畫面視覺斷裂。

### 4. SettingsScreen（設定）

`ui/settings/SettingsScreen.kt`。`ImagineScreen` 外殼（AppBar 標題「設定」、返回、無底部導覽）。單一滾動 `Column`，區段間距 24dp，每段以 `SectionHeader` 起頭。版本號 `BuildConfig.VERSION_NAME` 動態顯示（符合「單處 bump 版次」原則）。

**區段（順序即版面）**
1. **素材** — `ImagineCard` 入口（history icon）→ `onLibraryClick`，進素材庫。
2. **生成預設** — 圖片/影片每次進生成頁的初始參數，改即時寫 `prefs`。用 `ParamPicker`：圖片＝解析度(1k/2k)、長寬比(8 選含 auto)、數量(1–4)、品質(rapid/quality→快速/高品質)；影片＝秒數(1–15)、長寬比(7 選)、解析度(480p/720p)。
3. **API** — `ImagineCard` 入口（key icon）→ `onApiKeyClick`；顯示遮罩後 key（`maskKey`：前 4 + 13 點 + 後 3）+「已驗證 · 時間」綠字。
4. **xAI 後台** — 卡內兩列 `SettingRow`（外部連結，`open_in_new`）：在 console.x.ai 看用量/帳單（**team id 硬編在 URL**）、開啟 grok.com。註解說明已棄用 Management API 拉餘額（單位對不上），改開官方後台。
5. **安全** — 「防止截圖與錄影」`Switch`，即時 `applyScreenshotFlag(activity)` + 寫 prefs。
6. **歷史匯入** — 從相簿多選匯入 History（`PickMultipleVisualMedia`，最多 100，**免 READ_MEDIA 權限**，無 prompt）；亦支援外部 App「分享」進來。
7. **背景任務** — 說明影片背景 worker 會被電池優化殺掉，按鈕跳系統 App 詳細設定頁讓使用者改「不限制」（特別點名 Samsung）。
8. **除錯** — 分享 `CrashLogger` 內部 log / 清空兩鈕。
9. **Keys 備份** — CSV（key,value 兩欄）匯出走系統分享面板（避剪貼簿）/ 匯入開 `SimpleStringEditDialog` 貼 CSV（兼容舊 JSON）。
10. **危險區** — 預設**摺疊**，展開才露出紅色 `errorContainer`「清除所有資料」→ `ClearDataDialog` → `onClearedAndReset`。
11. **更新** — 「檢查更新」手動觸發 `UpdateChecker.check(null)`（繞過 2 分鐘 cooldown），有新版跳 `AlertDialog`（舊→新版本 + APK 大小）→ `Installer.downloadAndLaunch`，下載進度走 nav host 全域 UpdateBanner。
12. **關於** — 置中「Imagine v{版本}」+「xAI Imagine API 客戶端」。

**互動模式**：入口型用 `ImagineCard`（圓形 icon 容器 + 標題 + 副標 + chevron）；多列設定用 `SettingRow`（可選底部 0.5dp divider）；動作型用 `OutlinedActionButton`。所有 Dialog 走 Compose `Dialog`/`AlertDialog`。

**粗糙點**：區段樣式不統一——有的用 `ImagineCard(pad=0)` 包 `SettingRow`，有的 `ImagineCard(pad=16)` 直接放內容，有的（危險區）完全手刻 `Box`+`clickable`；`SectionHeader` + 卡片內再來一層標題文字偶有重複層級感。`formatApkSize` 因原 UpdateBanner 的 private 而重複實作一份。console.x.ai team id 與 usage URL 為硬編字串。整頁 12 段資訊密度高、無分組摺疊（除危險區）。

---

## UI 痛點與重設計機會 (Pain Points & Redesign Opportunities)

優先級由高至低：

1. **【高】分段控制（SegmentedTab）語意層級混亂**：同一外觀同時承擔頂層導航（圖片↔影片）、子模式（生圖/編輯）、4 選 1 功能（文生影/圖生影/延長/編輯）三種層級，影片頁更被迫拆成「兩排各 2 段＋只有一排高亮」的隱性表達。
   → *建議*：頂層圖片↔影片改用頁籤或左右滑（Pager），子模式改用次級分段或下拉；影片四模式改成單排可捲分段或 2×2 卡片網格，明確標示「4 選 1」，並用統一的選中態（保留 100dp pill + `check` 圖示語彙）。

2. **【高】生成頁上半部控制元件堆疊、層級不清**：圖片頁未生成前連堆色彩標頭 + 兩條導航/子模式 SegmentedTab + 品質 SegmentedTab 共四條橫條。
   → *建議*：合併「模式色彩標頭」進 AppBar（用 AppBar 底色或左側色標表達當前模式），品質與其他參數一律收進統一參數列，讓首屏優先露出 PromptInput 與「生成」。

3. **【高】全螢幕 Viewer 動作膠囊過度擁擠、重要動作藏在水平捲動之外**：我的素材 5 顆膠囊（生圖/生影/存相簿/改分類/移出）窄機放不下，靠無視覺提示的水平捲動，「改分類／移出」易被埋沒。
   → *建議*：主要動作（生圖/生影/存相簿）留在底部固定膠囊列，次要動作（改分類/移出）收進右上「⋯ 更多」選單或長按選單；或改用兩行 grid 動作面板，避免單行溢出。

4. **【中】移除/分類入口分散、語意混雜、可發現性差**：「移出素材庫」同時存在 Viewer 膠囊與詳情頁 `CategoryPickerRow`「再點取消」兩處，且用 `close` 圖示代表「移出」易誤解為「關閉」；分類取消手勢只靠文字說明。
   → *建議*：統一移除入口（單一明確「移出素材庫」動作 + 確認），改用語意正確圖示（如 `visibility_off` 或標籤移除圖示）；分類採可見的 toggle 態（選中填色 + 取消叉），不靠「再點同一個」的隱性手勢。

5. **【中】同一張圖的動作入口分散、命名不一致**：Viewer 膠囊、詳情頁 ActionRow、詳情頁 chip 三處都有動作，且「生圖」vs「編輯這張」、「生影」vs「動起來（生影片）」命名不統一。
   → *建議*：建立一套全 App 共用的「媒體動作」清單與固定文案/圖示，所有入口（結果區、Viewer、詳情頁）復用同一組件與字串。

6. **【中】結果區/生成中狀態實作分裂，視覺語言不一致**：結果動作鈕在圖片頁用 `ImagineChip`(FlowRow)、影片頁與 EditPane 用 `TextActionButton`(右對齊 Row)；生成中狀態三套實作（圖片頁只換按鈕字、影片頁/組合延長詳細卡、EditPane 簡卡）。
   → *建議*：抽出共用 `ResultActionBar` 與 `GeneratingCard`（可配置是否顯示計時器/進度條），三頁統一復用。

7. **【中】參數選項跨頁不對齊，形成隱性負擔**：圖片頁(解析度/長寬比/數量/品質) vs 影片頁(秒數/長寬比/解析度)，長寬比集合還不同（圖片有 auto、影片無）。
   → *建議*：統一 ParamPicker 容器與排版，長寬比選項集明確標注適用範圍；不適用的參數以 disabled/隱藏一致處理，避免使用者猜測。

8. **【中】手機鍵盤與窄機擠壓處理**：參數列三欄等寬時 ParamPicker 標籤（「長寬比」「解析度」）在窄機被擠；PromptInput 雖有 imePadding + `bringIntoView`，但整體 edge-to-edge 下 inset 全靠 Compose 吸收，易回歸。
   → *建議*：參數列改為可換行（FlowRow）或標籤上置值下置的雙行 picker；維持並回歸測試 imePadding / `bringIntoView` 行為。

9. **【中】卡片/容器樣式各處硬寫、不共用**：教學頁 `ActionRow`/`LessonCard`/`CatChip`、設定頁三種 card 包法、HubCard 硬寫 padding，導致圓角/內距/分隔線不一致。
   → *建議*：收斂到 `ImagineCard` + `SectionHeader` + 共用 chip/row 元件，移除自製重複樣式（如 `CatChip` 併入 `ImagineChip`）。

10. **【中】設定頁 12 段資訊密度過高、無分組**：除危險區外全部展開，外部連結/進階/危險混排。
    → *建議*：分組為「常用 / 進階 / 外部連結 / 危險」可摺疊群組，或把進階項收進次級頁。

11. **【低】首頁四卡無視覺優先級**：主要動作（生成圖/影）與次要（素材庫/Grok）扁平並列，純靠 emoji + 漸層區分，副標單行點分隔字串在窄機擠壓。
    → *建議*：用尺寸/位置區分主次（如圖片/影片大卡在上、素材庫/Grok 小卡在下），副標改用圖示標籤群而非長字串。

12. **【低】Grok 諮詢頁與全 App 視覺斷裂、整合脆弱**：內嵌外站 WebView、硬編 UA、結果回流依賴系統分享。
    → *建議*：明確標示「外部網頁」狀態列，提供「複製結果回 Imagine」的引導；UA 字串集中常數化便於更新。

13. **【低】死碼與重複實作**：LongVideo 的未使用 `IconBtn`、設定頁重複 `formatApkSize`、全形數字硬編區段標題、`showBalanceBar` 已不 render。
    → *建議*：清理死碼、抽公用 util、區段標題結構化（非字串內嵌數字）。

---

## 重設計時務必保留的硬約束 (Hard Constraints)

重設計可大幅調整版面與視覺，但下列必須保留，不得破壞：

1. **技術棧**：Jetpack Compose + Material 3。沿用 `ImagineTheme` / `MaterialTheme.colorScheme` token 體系與 `CompositionLocalProvider`（`LocalBudgetColors` / `LocalSpacing` / `LocalIsDark`）；不引入其他 UI 框架。

2. **導覽結構與狀態還原**：保留底部三 tab（`material` 素材生成 / `long_video` 長片組合 / `tutorial` 教學範本）與既有路由；保留 Hub 四卡分流、圖片↔影片互跳、編輯/延長/組合延長各入口的進入方式與返回行為（含 `BackHandler`、Grok WebView `goBack` 邏輯），以及可跨 process 恢復的狀態還原（`trackedRequestId` 作為 SSOT）。

3. **Edge-to-edge + imePadding**：維持 `MainActivity.enableEdgeToEdge()`（`decorFitsSystemWindows=false`）下由 Compose 吸收 inset 的模型；保留內容區 `.imePadding()` 與 PromptInput 的 `bringIntoViewRequester` 抬升行為、底部 Viewer 動作列避開三鍵列的 inset 推高；遵守 `ImagineScreen` 的 `scroll` 旗標約定（內嵌 `Lazy*` 必須傳 `scroll=false`，否則 infinity max height 崩潰）。

4. **繁體中文文案**：全介面維持繁中；既有功能字串語意（如「生成」「素材庫」「組合延長」「課程圖庫」）可整併統一，但不得改成其他語言。

5. **生成管線與狀態**：不得破壞 `runGenerate` 與 `VideoPollWorker` 背景輪詢流程、`appScope` 背景下載存相簿、`resultGen++` 換 Coil 快取 key、圖片編輯 scope-cancel 同步路徑與影片走 Worker 背景路徑的分工。生成中可切背景/鎖屏、完成發通知、「請勿從最近應用滑掉」的行為與提示必須保留。

6. **單一裝置鎖定**：維持單裝置、單帳號、本機儲存（filesDir/History/素材庫）模型；不引入雲端同步或多帳號。Keys 備份僅走系統分享面板的 CSV 匯出/入。

7. **不新增重型相依**：長片串接維持本機 `MediaMuxer`/`VideoMerger`（不花 API）、影片預覽維持 Media3 ExoPlayer、圖片載入維持 Coil；不為了視覺重設計引入大型新函式庫或服務。版本號維持 `BuildConfig.VERSION_NAME` 單處 bump、動態顯示。

---

## v1.7～v1.8.5 增補（2026-09-12）

本節補記 v1.7.0～v1.8.5 新增或改版的畫面與元件，寫法沿用前文（用途 / 版面結構 / 主要元件與狀態 / 互動 / 痛點），只描述程式碼裡確實存在的東西；標「觀察」者為從程式碼與版面推斷，標「待確認」者為程式碼中找不到明確依據。主要變化：App 從「xAI 單一供應商」變成「xAI + OpenRouter 雙供應商、依模型 id 決定走哪家」；新增 API 對話頁、模型列、素材去留審查、角色資產（名字＋定妝圖組）、三視圖工坊、鎖臉流程、app 內浮層通知；首頁改為三主卡＋工具列；全 App 改為強制深色（`Theme.kt`，`darkTheme` 參數保留但不再使用）。


### 舊章節與現況不符處（以本增補為準）

| # | 舊章節位置 | 舊寫法 | 現況 |
|---|---|---|---|
| 1 | 導覽地圖、素材生成首頁、痛點 | `MaterialHubScreen` 四卡分流、`HubCard` 漸層 | 三張 `PrimaryGenCard`（對話/生圖/生影）+ 三列 `ToolTile`；`HubCard` 與四組 `Brush.linearGradient` 已不存在 |
| 2 | 設計系統 | 深淺色由 `isSystemInDarkTheme()` 自動切換 | 全 App 強制深色（`Theme.kt`），系統列固定 dark style |
| 3 | 圖示集 | 圖示清單 | 缺 `chat, chevron_right, cloud_download, delete, forum, more_horiz, open_in_new, photo_library, receipt_long, sell, send, swap_horiz, thumb_up, thumb_down, visibility_off` |
| 4 | 生成圖片頁、痛點 #2 | 色彩標頭 + 圖片/影片 + 生圖/編輯 + 品質三層 SegmentedTab | L1「對話｜生圖｜生影」+ L2 `UnderlineTab` + `ModelPickerRow`；品質併入模型列；參數改 2 列 |
| 5 | 生成影片頁、痛點 #1 | 兩排各 2 段、只有一排高亮 | 單排橫滑 `ModePill` 5 選 1（含新「參考圖生影」）；OpenRouter 下縮成 3 選 1 |
| 6 | 導覽地圖、生成影片頁 | 組合延長是 `GenerateVideoScreen` 內分支 | 獨立路由 `COMBINE_EXTEND`，AppBar 帶返回鍵、無底欄，唯一入口為長片組合的 `VideoFramePicker` |
| 7 | 可重用元件、素材庫、痛點 #3/#4 | Viewer 底部可水平捲動膠囊列；素材庫 5 顆膠囊 | 固定列（≤4 全顯示、>4 前 3 + 「更多」浮層）；「移出素材庫」改 `visibility_off` 圖示標 destructive |
| 8 | 設定頁 | 生成預設含「品質」；API 卡單一 key；「xAI 後台」 | 「品質」已移除（v1.8.4）；API 卡列 xAI + OpenRouter 兩把 key；改名「帳單 / 後台」多 OpenRouter 餘額/活動紀錄；關於副標「xAI Imagine ／ OpenRouter API 客戶端」 |
| 9 | 導覽地圖路由表 | — | 缺 `SPLASH`、`CHAT`、`MATERIAL_REVIEW`、`COMBINE_EXTEND`；`VIDEO_GENERATING` 定義存在但無人使用 |

### 導覽地圖變更

底部三 tab（`material` / `long_video` / `tutorial`）不變。`Routes.SPLASH`（`SplashScreen`，800ms 後直接進 `MATERIAL_HUB`，PIN 已移除）為起點。新增三條路由（`nav/Routes.kt`）：`CHAT`（API 對話）、`MATERIAL_REVIEW`（素材總覽・去留審查）、`COMBINE_EXTEND`（組合延長獨立頁）。`VIDEO_GENERATING` 常數存在但全專案無人導向（觀察：死路由）。

```
Splash ─→ 素材生成 MaterialHubScreen（三主卡 + 工具列）
           ├─ 💬 對話 → ChatScreen ─────────┐
           ├─ 🖼 生圖 → GenerateImageScreen ─┤ 頂部 L1 SegmentedTab「對話｜生圖｜生影」三頁互切
           ├─ 🎬 生影 → GenerateVideoScreen ─┘ （popUpTo MATERIAL_HUB + saveState/restoreState）
           ├─ 工具：素材庫 → MaterialLibraryScreen
           ├─ 工具：素材總覽・去留審查 → MaterialReviewScreen（亦可從設定「素材」區進）
           └─ 工具：提示詞諮詢 Grok → GrokChatScreen（WebView，未變）
長片組合 LongVideoScreen → AvailRow movie 圖示 → VideoFramePicker
           └─ 「組合延長：接此片後」→ Routes.COMBINE_EXTEND（GenerateVideoScreen + onBack，無底欄）
設定 → API Key → ApiKeyEditScreen（xAI / OpenRouter 兩分頁）
```

三頁互切都錨定 `MATERIAL_HUB`（註解：不可錨 `GENERATE_IMAGE`，從 hub 直進影片頁時它不在 stack，`popUpTo` 不可達會讓 `saveState` 失效並一直疊頁）。`COMBINE_EXTEND` 刻意不走這個錨點，是「進階獨立頁」（註解原文），返回鍵 `popBackStack`。

### 1. API 對話頁 `ChatScreen`（`ui/chat/ChatScreen.kt`，`Routes.CHAT`）

**用途**：直接打 chat completions（xAI 或 OpenRouter，由所選模型 id 判斷供應商），非 WebView。與 Grok 諮詢頁並存。

**版面（`ImagineScreen`，`scroll = false`，內容 `padding(h16, v12)` / `spacedBy(10)`）**：
```
AppBar「對話」                      [receipt_long 帳單] [settings]
┌ SegmentedTab 對話｜生圖｜生影（activeColor 0xFF4A2E6E 紫）┐
┌ ModelPickerRow（模式 CHAT）                              ┐
（未設該家 key → 紅字 ImagineCard，點跳設定）
費用列 11sp mono：OpenRouter「本次對話 $x · 餘額 $y」/ xAI「費用以 console.x.ai 為準」  [帳單][清除]
LazyColumn（weight 1f）ChatBubble…；空時灰字說明；送出中 16dp spinner「思考中…」
┌ OutlinedTextField（maxLines 5，48–140dp）─────────┐ [48dp 送出方鈕 radius 14]
```

**主要元件與狀態**：`model`（`rememberSaveable`，寫回 `prefs.chatModel`）、`turns: List<ChatTurn(role,content,meta)>`（自訂 `listSaver`）、`sessionCost`、`balanceText`（OpenRouter `GET /credits`，進頁與每次回覆後刷新）。`ChatBubble`：`widthIn(max 320dp)`、半徑 14dp；user 靠右 `primaryContainer`、assistant 靠左 `surfaceContainerHigh`、error `errorContainer`；內文 `SelectionContainer` 14sp/20sp；非 user 泡泡下方 10sp mono meta（模型 · 本則 $ · tokens）＋ 14dp `content_copy`。

**互動**：送出鈕只在 `input` 非空、非送出中、且 `prefs.hasKeyFor(provider)` 時為 primary 色；新一輪自動 `animateScrollToItem` 到最後；「清除」直接清空對話與累計費用（無確認）；「帳單」用系統瀏覽器開 `provider.billingUrl`。

**觀察**：回覆非串流，整段一次落下；帳單入口重複兩處（AppBar icon + chip）；泡泡上限 320dp 在 384dp 寬機上幾乎滿版；金額 `$` 與 mono 字硬寫、無在地化；xAI 路徑沒有費用資料只顯示提示字。

### 2. 模型列 `ModelPickerRow` + `BadgePill`（`ui/component/ModelPicker.kt`）

**用途**：對話 / 生圖 / 生影三頁共用的模型選擇器。v1.8.3 起「有 key 的供應商合併成一份清單」（xAI 在前，OpenRouter 免費款排前；兩家都沒 key 時全列，生成鈕另擋）。

**列本體**：滿寬、半徑 12、`surfaceContainerHigh` 底 + 1dp `outline` 邊、內距 h12/v10。上「模型 · xAI/OpenRouter」11sp；中模型名 15sp/W600 最多 2 行（註解：384dp 大字體不擠）；下一排 `BadgePill` + 價格 11sp mono；右 22dp `expand_more`。

**`BadgePill`**（10sp/W700，半徑 6，內距 h6/v2，顏色硬編非 token）：`free` 綠 `#2E7D32 α0.28`/字 `#8BE08F`、`limited_free` 橘 `#E65100 α0.30`/`#FFB74D`、`conditional_free` 藍 `#1565C0 α0.30`/`#7EB8F5`、`variable` 灰、`paid` 透明底 + 1dp `outline` 框。文字由 `badgeLabel()` 給（免費 / 限時 / 條件 / 付費等）。`ProviderTag`（私有）：`surfaceContainerHighest` 底 10sp 供應商小標。

**底部清單**：`ModalBottomSheet`（`skipPartiallyExpanded`），內容 `fillMaxHeight(0.92f)`。標頭：「選對話/生圖/生影模型」15sp/W700 + 副標「N 個 · xAI 官方價 · OpenRouter 快照 {日期|內建}」；有 OpenRouter key 時右側「更新」`TextActionButton`（`OpenRouterCatalog.refresh` → `AppNotice`）。搜尋框 `OutlinedTextField`「搜尋名稱 / id / 免費」。`LazyColumn(weight 1f)` 每列三行：名稱（2 行）+ 選中勾／`ProviderTag` + `BadgePill` + 價格／id `· NK ctx`（對話）10sp mono + `badgeHint` 說明。選中列底 `primary α0.08`。

**狀態**：v1.8.4 `LaunchedEffect(models, selectedId)` — 選到的模型已不在清單（那家 key 被移除）時自動退回 `defaultModelFor(...)`，不留幽靈選項。

**觀察**：清單只靠 `ProviderTag` 區分兩家，無分組標題；價格字串長時單行省略；三頁都各自 `remember` 一份 catalog，切頁重算；badge 色與主題無關。

### 3. API Key 頁 `ApiKeyEditScreen`（`ui/settings/ApiKeyEditScreen.kt`）

**用途**：xAI / OpenRouter 各一把 key 的輸入 / 貼上 / 顯示 / 複製 / 移除。v1.8.3 拿掉「目前使用 / 改用」供應商切換——有 key 的供應商模型直接合併列在各頁模型清單。

**版面（AppBar「API Key」+ 返回，trailing 48dp 佔位，無底欄；區塊 `spacedBy(20)`）**：
1. `SegmentedTab`（`ApiProvider.entries`：「xAI (Grok)」/「OpenRouter」）。
2. 說明卡 `ImagineCard(pad 12)` 11sp：兩家價格 / 能力差異（圖片編輯・影片延長・影片編輯目前只有 xAI）。
3. 目前 Key 卡：「{label} KEY」12sp → 遮罩 key 15sp mono（`maskKey` 前 4 + 13 點 + 後 3）→ 綠色 `budgetColors.ok`「已設定 · 日期」→ `TextActionButton`「複製」「顯示完整/遮蔽」→「取得 Key」(`keysUrl`) 「查帳單 / 用量」(`billingUrl`)。
4. `SectionHeader("變更為新 Key（xAI）")` + 56dp `BasicTextField` mono（placeholder `keyHint`）+ 右對齊「從剪貼簿貼上」（`extractKeyFrom` 依前綴 `xai-` / `sk-or-` 從整段文字抽 key，忽略大小寫）+ 10sp 註「只讀最近一次複製；Samsung 剪貼簿歷史讀不到」。
5. `PrimaryButton`「儲存 {label} Key」— `canSave` = 前綴符合且長度 > 8；儲存即 `AppNotice.show` 並 `onSaved()` 退頁。
6. 移除區（有 key 才顯示）：`errorContainer` 圓角 16 盒，key 圖示 + 標題 + 「移除後這家的模型會從各頁模型清單消失」，**只有右側 `chevron_right` 是 clickable**，點下立即清 prefs、`AppNotice`、`onRemove()` 退頁，無確認框。
7. 底部置中 12sp「Key 由 Android Keystore 加密儲存於本機…」。

**狀態**：`tabId`（`rememberSaveable`，只有 OpenRouter key 時預設開 OpenRouter 頁）、`showFull` / `newKey` 綁 `remember(tabId)` 切頁即重設、`keyTick` 存/移除後刷新。

**觀察**：移除無二次確認且點擊區只有小箭頭（16dp 視覺、無 40dp 觸控盒）；貼上失敗走系統 `Toast`、儲存走 `AppNotice`，同頁兩套回饋；儲存後立刻退頁，浮層其實顯示在設定頁上。

### 4. 素材總覽・去留審查 `MaterialReviewScreen`（`ui/hub/MaterialReviewScreen.kt`）

**用途**：把 1000+ 內建素材（`MaterialSeed`）攤成可篩選格子，逐張點一下決定去留。丟棄 = `HiddenSeed.hide`（素材庫與課程圖庫同步消失，可復原）；保留 = `SeedReview.keep`（只為了之後能篩「未決定」把沒看過的挑完）。

**版面（AppBar「素材總覽・去留」+ 返回 + 齒輪，無底欄，`scroll = false`）**：
```
分類列（horizontalScroll）：全部 N ｜ 角色 N ｜ 環境 N ｜ 物件 N ｜ 風格 N        ← FilterPill
狀態列（horizontalScroll）：全部狀態 ｜ 未決定 N ｜ ✓ 保留 N(綠) ｜ ✕ 丟棄 N(紅)
動作列 FlowRow：[雲端更新 Tonal] [全保留 Outlined] [全丟棄 Outlined]   （v1.8.3 改 FlowRow，384dp 大字體自動換行）
10sp 提示：點一下:未決定 → ✓保留 → ✕丟棄;長按放大 · 素材更新於 {日期}
LazyVerticalGrid 3 欄（間距 6）ReviewCell…；空狀態置中「此篩選沒有圖片 / 沒有內建素材資料」
```

**`FilterPill`**（私有）：12sp、半徑 100 全圓、選中 `primary α0.16` 底 + `primary α0.4` 邊、否則透明 + `outline` 邊；保留/丟棄用固定色 `#5BD47A` / `#FF7B7B`。**`ReviewCell`**：正方、半徑 12、未決定 1dp `White α0.06` 邊，保留/丟棄 2dp 綠/紅邊；丟棄疊 `Black α0.55` 遮罩；右上 22dp 圓徽章（`#2E7D32` check / `#C62828` close）。

**互動**：點一格循環 未決定→保留→丟棄→未決定；長按開 `FullscreenImageViewer`（動作「保留」「丟棄(destructive)」，作用在當頁）；「全丟棄」先 `AlertDialog` 確認；「雲端更新」`SeedUpdater.update`（拉 repo main 的 `material_seed.json` / `tutorial_lessons.json`），成功 / 失敗皆 `AppNotice`。

**觀察**：兩條水平捲動列疊在一起，窄機上第二列的「丟棄」常在畫面外；`FilterPill` 是又一套自製 chip；循環式狀態要「按兩下」才能從保留回到未決定，無直接取消；1000+ CDN 圖走 Coil 無縮圖尺寸限制；狀態色硬編。

### 5. 首頁改版 `MaterialHubScreen`（v1.8）

**用途**：由四張同權重 `HubCard` 改為「三張主卡（對話 / 生圖 / 生影）+ 三列工具」，明確分主次（對應舊痛點 #11）。舊的 `HubCard` 與四組 `Brush.linearGradient` 已不存在。

**版面（`padding 16`，`spacedBy(14)`）**：
```
SectionHeader「要做什麼？」
┌ 對話 ┐┌ 生圖 ┐┌ 生影 ┐   ← Row，三張 PrimaryGenCard 各 weight 1f，間距 10
│ icon ││ icon ││ icon │     heightIn(min 168)、半徑 20、實色底 + 1dp base α0.32 邊
│      ││      ││      │     右上 90dp 徑向光暈（base α0.22 → 透明，offset 28/-28）
│ 標題 ││ 標題 ││ 標題 │     46dp icon 磚（半徑 14，base α0.16）/ 標題 20sp W900 #ECECF0
│ tags ││ tags ││ tags │     FlowRow 標籤 11sp（半徑 8，base α0.12）
SectionHeader「工具」
[photo_library] 素材庫  副標…                              ›   ← ToolTile
[thumb_up]      素材總覽・去留審查  1000+ 內建素材…         ›
[forum]         提示詞諮詢 ⌜Grok⌟  開啟 grok.com 網頁版    🌐
```

**顏色（全硬編）**：對話 `cardBg #1C1430 / base #B07CFF / icon #CFA8FF`；生圖 `#14182A / #6E8BFF / #9DB0FF`；生影 `#0F2422 / #2BD4C6 / #56E0D2`。`ToolTile`：底 `#17181D`、半徑 16、1dp `White α0.07` 邊、內距 14、icon 磚 46dp 半徑 13、標題 16sp/W700、badge 10sp 外框膠囊、副標 12sp `#9A9AA6`、右側 `chevron_right` 16dp 或 `language` 20dp，tint `#5A5B66`。生圖藍 `#9DB0FF` / 生影青 `#56E0D2` 同時被 `FullscreenImageViewer` 的動作 tint 與設定頁「圖片 / 影片」小標沿用，形成分區色。

**觀察**：三張卡在 384dp 寬只剩約 110dp 一張，標籤 `FlowRow` 必換行、卡高由 `heightIn(min)` 撐，三卡高度可能不齊；所有色值硬編、完全依賴強制深色；`onPickChat` / `onOpenReview` 為預設空 lambda 參數。

### 6. 設定頁新區塊 `SettingsScreen`

仍是 12 段，但內容有變（僅列 v1.7 後新增 / 改動）：
- **素材**：原「素材庫」卡（`history` 圖示，實際導向 `HistoryScreen`）下方新增一張 `ImagineCard(pad 0)` 兩列 `SettingRow`：「素材總覽・去留審查」→ `onReviewClick`；「從雲端更新素材（super-i 新課程）」（`cloud_download`，副標帶「上次 {日期}」，更新中標題改「更新中…」）。此列回饋走系統 `Toast`，與審查頁的 `AppNotice` 不一致。
- **生成預設**：v1.8.4 拿掉「品質」picker（快速 / 高品質就是生圖頁模型列的兩個 xAI 模型）；「圖片」「影片」小標改用分區色 `#9DB0FF` / `#56E0D2`。
- **API**：卡片改列兩行 mono「xAI  {遮罩}」「OpenRouter {遮罩}」，任一有 key 顯示綠字「有 key 的供應商模型會一起列出 · 點此管理」，否則紅字「尚未設定任何 Key」；右側 `expand_more`。
- **帳單 / 後台**（原「xAI 後台」）：四列 `SettingRow`：console.x.ai 用量/帳單（team id 仍硬編，且同一 URL 又複製一份在 `ApiProvider.XAI.billingUrl`）、openrouter.ai 餘額/帳單（`receipt_long`）、OpenRouter 活動紀錄（`https://openrouter.ai/activity`）、開啟 grok.com。
- **關於**：副標改「xAI Imagine ／ OpenRouter API 客戶端」。

### 7. 角色資產 `CharacterPickerSheet` / `SaveToCharacterDialog`（`ui/component/CharacterPickerSheet.kt`，v1.7.2）

**用途**：「角色資產」= 名字 + 一組定妝圖（`CharacterAssets`，只存名字與圖檔名的組合，圖檔仍在歷史/素材庫）。生成時一鍵帶入整組當參考圖（鎖臉 / 鎖造型）。

**`CharacterPickerSheet`**：`ModalBottomSheet`，內容 `heightIn(max 520)` + `verticalScroll`。`SectionHeader("選擇角色（帶入整組定妝圖）")`；無角色時 13sp 說明「生圖成功後在結果卡點『🎭 存成角色資產』…」；有角色時 11sp「點角色帶入…長按可刪除」+ 每角色一列（半徑 14、`surface` 底、`combinedClickable`）：「🎭 名字」14sp/W700 + 「N 張」→ 最多 4 張 52dp 縮圖 + 「+n」盒 → `chevron_right`。長按 → `AlertDialog`「刪除角色」（只刪組合不刪圖）。

**`SaveToCharacterDialog`**：`AlertDialog`「存成角色資產」：說明文（同角色多套定妝建議用「名字·定妝」）+ `OutlinedTextField`「角色名」+「或加進既有角色:」`FlowRow` `ImagineChip`（選中 Tonal）；「儲存」需非空。

**三個入口**：
1. `GenerateImageScreen` 結果卡「🎭 存成角色資產」chip：先跟 `imagine_batch_saved` 帳本對帳（`batchToken` UUID 對齊），整批都存好才開對話框，否則 `Toast`「圖片儲存中(n/N)」；確認後 `CharacterAssets.addImages` + 標素材庫「角色」分類。對話框用開啟當下的 `saveCharacterNames` 快照，防新批完成把清單換掉。
2. `GenerateVideoScreen` 參考圖生影模式「🎭 帶入角色定妝圖」chip：整組**取代**目前參考圖，最多 `maxImages`=3，超過 `Toast` 提示。
3. `EditPane(ImageEdit)`「🎭 加角色參考（鎖臉）」：取前 2 張，與來源共 3 張送 `/images/edits`。

**觀察**：長按刪除無視覺提示；三處帶入上限（3 / 2）與取代 / 追加語意不同，靠 `Toast` 說明；標籤全帶 emoji；每次開 sheet 重讀 `CharacterAssets.names`。

### 8. 三視圖工坊 `TurnaroundTab`（`ui/component/TurnaroundLab.kt`）

**用途**：參數化三視圖（正面 / ¾ 側 / 背面）prompt 生成器，掛在 `PromptTemplateSheet` 的「三視圖工坊」分頁（僅圖片模式 `!forVideo`；影片模式該位置是「分鏡」）。全女角、photorealistic 版式。資料字典 + 拼裝引擎 + UI 同檔。

**版面（父層 sheet 已 `verticalScroll`，本 tab 不自帶捲動）**：
```
① 畫幅            ← 5 顆 TurnChip 水平捲動（16:9 / 21:9 / 3:4 / 9:16 / 1:1）
② 人種／族裔 · 留「隨機」自動配   ← 14 顆（含隨機）
③ 角色原型 · 點一張立即生成       ← 6 大類 chip；下方 2 欄角色卡（半徑 12，選中 primaryContainer，13.5sp 名 + 11sp 描述 2 行）
④ 自助細節 · 留「隨機」每次擲新的 ← 年齡 / 體型 / 氣質 / 光線氛圍 四列，點列展開一排 chip，值前綴 🎲
[🎲 完全隨機一鍵出]（滿寬 primaryContainer）
附英文結構詞（出圖更穩）                       [Switch]
生成的三視圖提示詞                 [文字][JSON]
┌ 輸出框（surface，半徑 12，13sp/20sp）┐
                     [換細節重生] [複製] [使用]
```

**狀態**：`resolved: TurnResolved` — 隨機欄在 `resolveTurnaround` 當下擲定，之後切「文字 / JSON」、開關英文只重渲染不重擲（註解強調「內容不會跳」）。JSON 走共用 `PRETTY_JSON`，中文 key。「使用」→ `onPick(result)` 回填 `PromptInput` 並關 sheet。

**觀察**：`TurnChip` / `TurnSectionLabel` 又是一套私有 chip / 標題；區段編號 ①② 硬寫在字串；四排水平捲動列疊在一個垂直捲動 sheet 裡；狀態只用 `remember`，關 sheet 即遺失設定；輸出可能超長，輸出框無高度上限。

### 9. 生成頁 v1.7～1.8 變更（`GenerateImageScreen` / `GenerateVideoScreen`）

**共同**：
- 頂部「模式色彩標頭 + 圖片/影片 2 段」改為 **L1 `SegmentedTab` 三段「對話｜生圖｜生影」**，各頁 `activeColor` 不同（生圖 `#2E3A6E`、生影 `#14463F`、對話 `#4A2E6E`）。
- **`ModelPickerRow`** 放在 `PromptInput` 之後、參數列之前（對話頁則在最上方）；模型寫回 `prefs.imageModel` / `prefs.videoModel`。
- 生成鈕 `enabled` 改看 `prefs.hasKeyFor(provider)`；未設該家 key 顯示紅字卡「…或在模型清單改選另一家的模型」。
- 「存相簿」回饋改 `AppNotice`；其餘（生成完成、錯誤、角色帶入）仍是系統 `Toast`。

**生圖頁**：
- L2 子模式改 **`UnderlineTab`**（私有：14sp、2dp `primary` 底線、段距 24dp）「生圖 / 圖片編輯」，取代原 `SegmentedTab`（註解：與 L1 pill 視覺區隔，痛點 #1）。OpenRouter + 圖片編輯時多一行 11sp 提示「圖片編輯目前只接 xAI」。
- 品質 `SegmentedTab` 移除：快速 / 高品質就是模型列裡兩個 xAI 模型 id。
- 參數：xAI 走 2 列 `ParamPicker`（解析度 / 長寬比；數量 1–10 單獨一列佔半寬）；OpenRouter 分支選項來自模型 `supported_parameters`（`orResolutions` / `orAspects` / `orNMax`，`displayName` 加「（此模型一次 1 張）」），結果以 base64 直接落地成 `file://`。
- 結果 meta 在 OpenRouter 多帶模型 id 與「本次 $」；動作 `FlowRow` 後新增「🎭 存成角色資產」+「或設為其他分類」分類 chip 群。
- `FullscreenImageViewer` 動作：存相簿 / 分享 / 編輯 / 動起來（剛好 4 顆，全部固定顯示）。

**生影頁**：
- 「模式」改 **單排可橫滑 `ModePill`**（私有：半徑 100、選中 `#16433D` 底 + `#56E0D2 α0.5` 邊 + `#7FE9DD` check 與字），`SectionHeader` 動態標「模式・5 選 1」（xAI）/「模式・3 選 1」（OpenRouter）。選項：文生影 / 圖生影（OpenRouter 依模型 `frameImages` 才顯示）/ **參考圖生影（新 `VideoMode.Ref2Vid`）** / 影片延長 / 影片編輯（後兩者僅 xAI）。OpenRouter 下方多一行 11sp 支援度說明。
- 來源區標題依模式「起始圖」/「參考圖（可多張，不會被當成第一幀）」；`maxImages` 圖生影 1、其餘 3；參考圖生影多「🎭 帶入角色定妝圖」chip。
- 三顆 `ParamPicker`（秒數 / 長寬比 / 解析度）選項由模型 catalog 給（`durations` / `aspects` / `resolutions`），使用者偏好不在清單時以 `effDuration` 等最接近合法值顯示與送出、不改 prefs。
- 生成中卡改「36sp 估算百分比為主 + 11sp 經過秒數為輔 + `LinearProgressIndicator` + 紅字警告」；`VideoPollWorker` 多 `provider` 參數。

**PromptInput 工具鏈（`PromptTemplate.kt`，僅列入口）**：空白時「套用範本」開 `ApplyTemplateSheet`、「自己組」開 `PromptTemplateSheet`。後者分頁：自己組 / 分鏡（影片）/ 三視圖工坊（圖片）。「自己組」內：**✨ AI 填表**列（`OutlinedTextField` 一句話點子 + `TextActionButton`「AI 填表」→ `repository.chatOnce` 走 xAI key，回 JSON 填回欄位，每按一次一次 chat 呼叫）；「預覽」右側「文字 / JSON」`ReadyCatChip` 切換（JSON = 複製到 Seedance/即夢等平台或當 LLM 填表 schema）；底部「貼上JSON」（剪貼簿 `{欄位:值}` 回填，圖片模式濾掉影片限定欄位）/「複製」/「使用」。外貌特徵大類含**人物 DNA 三欄**「眼型眼色 / 鼻形 / 唇形」（註解：鎖同一人時只固定髮型髮色+眼+鼻+唇五特徵，真鎖臉仍要配 Base Image / 參考圖）。

**觀察**：同一頁並存三種選擇語彙（L1 `SegmentedTab` pill、L2 `UnderlineTab`、`ModePill`）；`ModelPickerRow` 在三頁的位置不一致；AI 填表只走 xAI（只設 OpenRouter key 時會失敗，待確認錯誤呈現）；OpenRouter 分支參數列與 xAI 分支排版不同（2×2 vs 2+1）。

### 10. 編輯頁鎖臉 `EditPane(ImageEdit)`（`ui/edit/EditScreen.kt`）

- 「來源」框下方新增**角色參考**區：`ImagineChip`「🎭 加角色參考（鎖臉）」（開 `CharacterPickerSheet`）；帶入後顯示 11sp「prompt 記得指名參考誰的臉」與 64dp 縮圖列（右上 20dp 圓形 close 可逐張移除）；上限 2 張，與來源共 3 張（xAI `/images/edits` 上限）——註解稱「super-i 雙參考圖法：來源當造型底、角色定妝圖鎖臉」。圖↔影越界切換時清空。
- 「編輯說明」標題下新增兩顆 `TextActionButton`：「🎭 三相圖範本」（`auto_awesome`，寫入一段固定三視圖角色設定表 prompt）與「🔒 鎖臉咒語」（`lock`，寫入 `IDENTITY_LOCK_SPELL` 常數：Base Image 身份鎖 + 頭部比例鎖，與 `READY_PROMPTS`「鎖臉咒語·Base Image 換景」共用同一份）。兩者**直接覆寫**目前 prompt。`PromptInput` 改 `label = ""`、`minHeight = 104`。
- 「執 行」仍要求 `prefs.isApiKeySet`（xAI）；OpenRouter 無此功能。

**觀察**：「三相圖」與其他處「三視圖」用詞不一；覆寫 prompt 無確認；角色參考回饋走 `Toast`。

### 11. App 內浮層通知 `AppNotice` / `AppNoticeHost`（`ui/component/AppNotice.kt`）

**用途**：取代系統 `Toast`。註解：One UI（Android 13+）在 app 沒有通知權限時會把系統 Toast 整個吃掉（本 app 未申請 `POST_NOTIFICATIONS`），「存相簿」等回饋完全看不到。

**實作**：`object AppNotice { current = mutableStateOf<Pair<Long,String>?> ; show(msg) }`。`AppNoticeHost`：`LaunchedEffect` 1800ms 後自動清除（新訊息直接取代舊的）；`Box.fillMaxSize().statusBarsPadding()` 置頂中，`padding(top 64)`，膠囊半徑 22、底 `#141418 α0.94`、1dp `White α0.14` 邊、內距 h18/v11、字 `#ECECF0` 13sp/W600；無 `clickable` / `pointerInput`，不攔觸控。

**掛載處（兩處，註解明言）**：`MainActivity` 根層 `Box` 蓋在 `ImagineRoot()` 上；`FullscreenImageViewer` 的 `Dialog` 內（Dialog 視窗蓋住根層，要自己掛一份）。

**目前使用者**：各頁「存相簿」、`ModelPickerRow` 更新、`MaterialReviewScreen` 全部回饋、`ApiKeyEditScreen` 儲存/移除、`MaterialLibraryScreen` / `HistoryDetailScreen` / `LongVideoScreen` 部分回饋。

**觀察**：`Toast` 與 `AppNotice` 混用面很廣（生成頁、EditPane、`VideoFramePicker`、`CharacterPickerSheet` 入口、設定頁雲端更新仍用 `Toast`）；`FullscreenVideoPlayer`、`VideoFramePicker` 等其他 `Dialog` 未掛 host，其內部的 `Toast` 正是會被 One UI 吃掉的情境；單槽、固定 1.8 秒、無 queue；`top 64dp` 恰好落在 AppBar 標題帶上。

### 12. 組合延長獨立頁 `Routes.COMBINE_EXTEND`

**進入點（唯一）**：長片組合 → 可用片段列 movie 圖示 → `VideoFramePicker` → 「組合延長：接此片後（新片自動串接）」→ 擷取畫格存成圖 → nav 在 `savedStateHandle` 放 `KEY_INIT_MEDIA`（尾格）、`KEY_INIT_VIDEO_MODE = "i2v"`、`KEY_INIT_EXTEND_BASE`（原片 `file://`）→ `navigate(COMBINE_EXTEND)`。

**畫面**：仍是 `GenerateVideoScreen`，但 `onBack != null`、`initialExtendBase != null`：AppBar 改「🔗 組合延長」+ 返回鍵（trailing 40dp 佔位）、`bottomNav = null`；內容走 `return@Column` 早退分支：
```
[🔗 組合延長] 青綠 #0F5E57 膠囊 15sp
ImagineCard 說明：用原片尾格當起點…完成後自動接成長片存到歷史「組合延長」
SectionHeader「尾格（續接起點）」 + SelectedImageSlot（onRemove = null，不可刪）
PromptInput（minHeight 88，placeholder「描述續集要怎麼動…」，videoHasImage）
ParamPicker 秒數（滿寬，1–15）
12sp「解析度自動沿用原片（480p/720p）」 ← LaunchedEffect 讀原片高度 ≥720 → 720p
生成中卡（48dp spinner / 36sp % / mono 經過秒 / LinearProgress / 紅字勿滑掉）  或  PrimaryButton「生成續集 → 自動接成長片」
錯誤卡 / ✅ 續集已生成 提示 + VideoPreview
```
成功後 `VideoPollWorker` 依 `initialExtendBase` 自動把「原片 + 續集」串成一支存進歷史。`ImagineNavHost` 對此路由把 `onSwitchToImage` / `onSettingsClick` / `onNavSelected` 全傳空 lambda。

**觀察**：AppBar 標題與內容第一行都是「🔗 組合延長」，重複；此分支不顯示 `ModelPickerRow`，但仍用 `prefs.videoModel` 決定供應商——若上次選的是 OpenRouter 模型，組合延長會走 OpenRouter 路徑（待確認是否預期）；整段仍是 `GenerateVideoScreen` 內的 `return@Column` 分支而非獨立 Composable（舊痛點延續）。

### 13. Fullscreen viewer / VideoFramePicker

**`FullscreenImageViewer` 底部動作列改版**（對應舊痛點 #3 / #4）：
- 動作 ≤ 4 顆：全部固定顯示；> 4 顆：前 3 顆 + 「更多」(`more_horiz`，highlight 底 `White α0.10`)，其餘收進上方 210dp 寬浮層（半徑 16、底 `#1A1B20`、列高 h12/v11、`destructive` 紅字 `#F0A0A0`、列間 1dp 分隔）。
- 固定列：滿寬、半徑 22、底 `#141418 α0.92`、1dp `White α0.10` 邊、內距 9；每格 `ViewerActionSlot` `weight 1f`，icon 22dp 疊 11sp label；tint 依 icon：`image` 藍 `#9DB0FF`、`movie` 青 `#56E0D2`、其餘白（配合首頁分區色）。
- 底距 = Dialog root window 導覽列 inset + 40dp、保底 80dp（註解：Compose `navigationBarsPadding` 在 Dialog 內常回 0）。
- 內部掛 `AppNoticeHost`。`ZoomableImage`、頁碼徽章、`HorizontalPager` 行為不變。
- 各處動作集：素材庫我的素材 5 顆 → 生圖 / 生影 / 存相簿 + 更多（改分類 `sell`、移出素材庫 `visibility_off` destructive）；內建素材 3 顆；審查頁 保留 / 丟棄；生圖結果 4 顆；教學 重繪 / 動起來；歷史詳情 存相簿 / 分享。

**`FullscreenVideoPlayer`**：未變（黑底 + `PlayerView` + 左上 close），未掛 `AppNoticeHost`。

**`VideoFramePicker`**（`ui/component/VideoFramePicker.kt`）：置中卡片式 `Dialog`（註解：按鈕不在螢幕底，避開手勢列），半徑 16、`surfaceContainerHigh`、內距 16。標題「擷取畫格（數字人 / 來源圖）」15sp/W700 → 畫格框 `heightIn(160–320)`（解碼中 spinner）→ `Slider` + 12sp「第 x.x 秒 / y.y 秒」→ 三列 `PickerAction`（滿寬、半徑 12、`surface` 底、icon 20 + 14sp/W600）：「用此格圖生影」(`movie`) /「存為角色素材（數字人）」(`image`，`MaterialLibrary.setCategory` 角色) /「組合延長：接此片後」(`add`) → 11sp 提示「拉到接近尾端的畫格最順…」。拉桿停 120ms 才解碼（`MediaMetadataRetriever` `OPTION_CLOSEST_SYNC`）。

**觀察**：`VideoFramePicker` 三個動作都先 `saveFrame` 落地成圖再回呼，取消不會清掉已存的圖（會出現在歷史）；所有回饋走 `Toast`；「更多」浮層無 scrim，點外面不會關閉（只有再點「更多」或點某項）；固定列 label 11sp 在四顆等分時每格約 85dp，長標籤（「移出素材庫」進浮層才安全）。

### 重設計硬約束（新增）

只列 v1.7 後新出現、程式碼註解或結構明示的雷區；前文「硬約束」1–7 仍全部有效。

1. **強制深色**：`ImagineTheme` 忽略系統淺 / 深（`Theme.kt` 註解「重設計(Claude Design handoff)：全 App 強制深色」），`MainActivity.enableEdgeToEdge` 固定 `SystemBarStyle.dark(TRANSPARENT)`。首頁三主卡 / `ToolTile` / viewer 動作列 / `AppNotice` / `ModePill` / `BadgePill` 全用硬編深色值，**不可**在未重做這些色值的情況下恢復淺色模式。
2. **回饋不得依賴系統 Toast**：One UI 在無 `POST_NOTIFICATIONS` 時吃掉 Toast（`AppNotice.kt` 註解）。新 UI 的操作回饋走 `AppNotice.show`；任何新的 `Dialog` 視窗若要顯示回饋，必須像 `FullscreenImageViewer` 一樣自掛一份 `AppNoticeHost`（Dialog 蓋住 Activity 根層那份）。
3. **Dialog 內 inset**：全螢幕 viewer / player 維持 `DialogProperties(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)`；底部動作距離改用 `ViewCompat.getRootWindowInsets(view)` 取真實導覽列高 + 40dp、保底 80dp（Compose `navigationBarsPadding` 在 Dialog 內常回 0）。viewer 動作 > 4 顆必須進「更多」浮層，不可回到單行水平捲動。
4. **384dp 大字體**：S22U 開大字體 / 顯示大小時可用寬僅約 384dp。chip 列要用 `FlowRow`（`MaterialReviewScreen` 動作列 v1.8.3 改法、首頁標籤、生圖結果動作）；模型名稱允許 2 行（`ModelPickerRow`）；三顆等分 `ParamPicker` / 三張等分主卡是已知擠壓點。
5. **`ModalBottomSheet` 慣例**：一律 `skipPartiallyExpanded = true`、`containerColor = surfaceContainerHigh`。內含 `LazyColumn` 的 sheet 要給高度上限（`ModelPickerRow` 用 `fillMaxHeight(0.92f)` + `weight(1f)`；`LibraryImagePickerSheet` `heightIn(max 420)`）；`CharacterPickerSheet` / `PromptTemplateSheet` 用 `verticalScroll` 而非 `Lazy*`。`PromptTemplateSheet` 的 `tab` 綁 `remember(forVideo)`，模式切換分頁集合不同時要重設。
6. **三頁互切導覽**：`CHAT` / `GENERATE_IMAGE` / `GENERATE_VIDEO` 互切必須 `popUpTo(MATERIAL_HUB){saveState}` + `launchSingleTop` + `restoreState`（`ImagineNavHost` 註解說明錯錨會疊頁）；`COMBINE_EXTEND` 是獨立路由（`onBack`、無底欄），不得併回素材生成 tab。`savedStateHandle` 的 `KEY_INIT_*` 一次性消費並清除，新入口要同樣清 gate。
7. **`FLAG_SECURE` 預設開**：`prefs.preventScreenshots` 預設 `true`，`MainActivity.onCreate` 即套用；設定「安全」`Switch` 即時 `applyScreenshotFlag`。重設計截圖 / 錄影驗收時需先關閉，否則畫面全黑。
8. **裝置鎖**：`MainActivity.isAllowedDevice()` 依 `ALLOWED_MODEL_PREFIXES` 只允許 S22U，否則 `AlertDialog` 後 `finishAffinity`——版面只需針對此單一機型。
9. **生成 / 背景**：生成中卡的「可切背景/鎖屏,完成發通知」與紅字「請勿從最近應用程式滑掉」文案保留；`VideoPollWorker` 多 `provider` 參數（xAI / OpenRouter），組合延長靠 Worker 讀 `initialExtendBase` 自動串接。程式碼中**未見**任何鎖屏 overlay / `SHOW_WHEN_LOCKED` 實作（待確認；若指的是 `appScope` 背景下載，前文硬約束 5 已涵蓋）。
10. **批次存檔帳本與快照**：生圖頁 `savedNames` / `batchToken`（UUID）/ `imagine_batch_saved` SharedPreferences 帳本，以及 `SaveToCharacterDialog` 用 `saveCharacterNames` 快照——重排結果卡時不得拆掉這套對帳，否則「存成角色資產」會寫進錯批圖片（v1.7.2 註解）。
11. **`EditPane` 約定**：同頁同時只渲染一個 `EditPane`（多個 Worker observer 會搶 `trackedRequestId`）；不自帶 padding；ImageEdit 角色參考上限 2 張（與來源共 3 張是 API 上限）。
12. **模型選擇必在清單內**：`ModelPickerRow` 的 v1.8.4 fallback（選中模型不在清單即退回 `defaultModelFor`）與 `prefs.chatModel/imageModel/videoModel` 持久化要保留；供應商永遠由 `ApiProvider.ofModel(id)` 推導，不另存「目前供應商」。
13. **`rememberSaveable` 綁 prefs 預設 key**：生成頁參數 `rememberSaveable(prefs.defXxx)`，改設定回頁才會 re-init；新增參數請沿用同一寫法。
