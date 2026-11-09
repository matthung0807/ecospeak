# EcoSpeak

## Using the practice page

1. Open the home page and enter a scenario, such as `Ordering coffee`.
2. Click **Generate Dialogue**. Empty or whitespace-only input shows
   `Please enter a scenario.` without sending a request.
3. Read the returned topic, target phrase, and A/B dialogue on the same page.
4. Edit the retained scenario and click **Generate Another** to generate again.

While generating, the button is disabled and the previous result stays visible.
If generation fails, the page shows `Something went wrong. Please try again.`;
the input and previous result remain available for editing and retry.

This implements US-01 and the missing input-dependent portion of FR-03 from
`docs/product_proposal.md`. It does not establish generated language quality or
learning effectiveness.

## Generation API

Run with Java 25 and `OPENAI_API_KEY` set: `./mvnw spring-boot:run`.
The default URL is `http://localhost:8080`; `PORT` can override the port.

`POST /api/dialogs/generate` accepts a JSON object with a required string `scenario`:

```sh
curl -X POST http://localhost:8080/api/dialogs/generate \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"Ordering coffee"}'
```

A successful response is HTTP 200 with `topic`, `targetPhrase`, and a `dialogue`
array of `{ "speaker": "A", "text": "..." }` entries. The backend includes the
scenario in its structured AI generation request. The AI selects the target phrase.

Missing bodies and missing, null, empty, or whitespace-only scenarios return
HTTP 400 before AI generation. Surrounding whitespace is removed using JavaScript
`trim()` semantics; internal whitespace is preserved. The former input-free
`GET /api/dialogs/generate` contract is retired and returns HTTP 405.

The prompt requests a natural A/B conversation of 4–5 lines with the target phrase.
These content constraints are prompt instructions, not application-level checks of
the generated dialogue's language quality, length, or phrase occurrence.

Run backend tests with `./mvnw test` using Java 25. Tests mock provider access and
do not need a real API key.

## Earlier PRD

The document below is retained as earlier product planning. Current MVP intent is
in `docs/product_proposal.md`; implementation differences are tracked in
`docs/implementation-comparison.md`.

# PRD：輕量化英語口說情境生成 App（MVP）

## 文件資訊

| 項目 | 內容 |
|---|---|
| 產品名稱 | EcoSpeak（暫定） |
| 產品定位 | 輕量化 AI 英語口說情境練習 App |
| 文件版本 | v1.1（MVP） |
| 撰寫人 | 肉豬 |
| 撰寫日期 | 2026-10-18 |
| 文件狀態 | Draft / 待對齊 |

## 1. 產品背景與痛點（Background & Problem Statement）

### 1.1 背景（Background）

部分中高級英語學習者具備良好的聽力與閱讀理解能力，能理解許多單字及片語，但在實際口說時，仍可能無法即時想起並自然使用已經學過的表達方式。

例如，學習者知道「We have a deal」的意思，卻不一定能在適當的對話情境中主動使用這個句子。

本產品希望透過短篇情境對話，將目標片語放入實際使用情境中，協助使用者增加主動輸出的機會。

### 1.2 現有解決方案的局限（Existing Pain Points）

- **AI 英語聊天 App：** 自由對話雖然能提供口說練習機會，但使用者可能持續依賴熟悉的單字與句型，未必能針對特定片語進行有系統的反覆練習。
- **線上真人家教：** 能提供實際對話機會，但在自由對談過程中，使用者仍可能習慣使用既有的表達方式，難以持續練習特定的新句型。
- **自行製作練習教材：** 使用者雖可自行透過生成式 AI 建立練習對話，但每次都需要重新描述學習需求、對話長度、程度及輸出格式，缺乏針對短時間口說練習設計的固定使用流程。

### 1.3 核心價值主張（Value Proposition）

透過 AI 自動生成短小、自然且符合指定情境的雙人英語對話，提供低負擔、可重複的口說練習方式。

核心原則：

- **小量：** 每段對話限制在 5 句台詞以內。
- **高頻：** 鼓勵使用者利用零碎時間進行短時間練習。
- **重複：** 使用者可針對當次產生的短篇對話進行反覆閱讀與口說練習。
- **情境化：** 將單字與片語放進實際生活情境中，增加理解及應用機會。

## 2. 目標使用者與產品範疇（Target Persona & Scope）

### 2.1 目標使用者（Target Persona）

**主要使用者：中高級英語學習者**

- 具備一定的英語單字量及文法基礎。
- 能理解許多英語句子，但口說時容易依賴熟悉的單字及句型。
- 希望學習並練習更自然的日常英語表達方式。
- 希望利用每日 3–5 分鐘進行低負擔的學習。

多益 800 分以上可作為初步描述目標族群的參考條件，但不應視為唯一的使用者資格。

### 2.2 產品範疇（In-Scope vs. Out-of-Scope）

#### In-Scope：MVP 本期要做

- 一鍵生成雙人英語情境對話，總共 5 句台詞以內。
- 顯示生成的情境名稱、目標片語及對話文字。

#### Out-of-Scope：MVP 本期不做

- 儲存歷史對話，提供清單瀏覽及重新查看功能。
- 對超過保存期限的歷史對話進行自動清理。
- 產生兩種不同音色的語音，並提供播放功能。
- 提供每日學習提醒及提醒時間設定功能。
- 即時語音發音評估及評分。
- 自由形式的 AI 語音對話。
- 複雜的文法糾錯及個人化學習分析。
- 完整的課程、單字測驗及學習進度管理系統。

## 3. 功能需求與 UI/UX 流程（Functional Requirements）

### 3.1 核心功能列表（Feature List）

| Feature ID | 功能名稱 | 優先級 | 功能描述 |
|---|---|---|---|
| FE-01 | 一鍵情境對話生成 | P0 | 使用者點擊按鈕後，系統產生兩位角色、總共 5 句台詞以內的自然英語對話，並顯示情境名稱及單一目標片語。 |

### 3.2 使用者流程（User Journey）

#### 流程一：產生情境對話

- 使用者進入主畫面。
- 使用者點擊「Generate Dialogue」。
- 系統顯示載入狀態。
- 系統產生情境名稱、目標片語及對話文字。
- 使用者可閱讀及練習產生的對話，或再次產生新的情境對話。

### 3.3 主要畫面

- **Home：** 提供情境對話生成功能，並於同一畫面顯示生成結果，包括情境名稱、目標片語及角色台詞。

### 3.4 功能驗收條件（Acceptance Criteria）

#### FE-01：一鍵情境對話生成

- 點擊按鈕後，系統能產生一段包含兩位角色的英語對話。
- 對話總數為 4–5 句台詞，不得超過 5 句。
- 每次生成應選擇一個自然且符合情境的目標片語作為主要練習內容。
- 目標片語必須實際且自然地出現在對話中。
- 系統必須處理生成失敗或回傳格式不正確的情況，並向使用者顯示錯誤訊息。
- 生成期間應顯示載入狀態，避免使用者重複送出請求。

## 4. AI 系統規格與 Prompt 設計（AI System Specifications）

### 4.1 LLM 生成規格（LLM Generation Spec）

**模型選擇：**

MVP 使用 GPT 系列中適合短文本生成的模型，後續可依生成品質、成本及延遲測試結果調整模型。

**System Prompt 初稿：**

```text
You are an expert English language coach focusing on
authentic everyday conversation for upper-intermediate learners.

Generate one short, realistic everyday English conversation.

Requirements:
- The conversation must contain exactly two participants: A and B.
- Generate 4 to 5 dialogue lines in total.
- Each dialogue line must have exactly one speaker: A or B.
- Choose exactly one natural and useful expression as the target phrase.
- The target phrase must appear naturally in the dialogue.
- Focus the conversation on practicing that target phrase.
- Use natural, conversational English appropriate for everyday situations.
- Avoid unnecessarily complicated vocabulary and textbook-style expressions.
- Give the scenario a short descriptive topic.
```

**備註：**

- 系統使用 Structured Outputs 約束 LLM 回傳資料結構，由程式定義 `topic`、`targetPhrase` 與 `dialogue` 等欄位，不單純依賴 Prompt 要求模型輸出 JSON。
- `dialogue` 應包含 4–5 筆元素。
- `speaker` 僅允許 `A` 或 `B`。
- 每次生成僅選擇一個主要 `targetPhrase`。
- `targetPhrase` 必須實際且自然地出現在對話中。
- 應由程式及 Structured Outputs 約束輸出格式，避免只依賴 Prompt。
- 生成的英文是否自然、目標片語難度是否符合目標使用者，以及目標片語是否使用正確，仍需透過實際生成樣本及使用者測試進行驗證。

MVP 優先採用容易實作、輸出結構穩定且能快速進行產品驗證的方案。

## 5. 非功能性需求與成本評估（Non-Functional Requirements）

### 5.1 效能與使用體驗（Performance & Latency）

- **文字生成時間：** 需實際測試並設定可接受的回應時間。
- **載入狀態：** 生成期間應顯示 Loading 狀態，避免使用者誤以為系統沒有反應。
- **錯誤處理：** 當 LLM 服務失敗時，應顯示清楚的錯誤訊息，並提供合理的重試方式。

4 秒內完成文字生成可列為初步效能目標，但須經實際測試確認可行性。

### 5.3 API 成本評估（Unit Economics）

以下為初步估算，並非已確認的實際報價。

| 項目 | 初步估算 |
|---|---|
| LLM 文字生成 | 約 USD 0.0003／次 |
| 單次生成總成本 | 約 USD 0.0003／次 |
| 新台幣換算 | 約 NT$ 0.01／次，依匯率而定 |

實際成本取決於模型、輸入及輸出長度、API 計費規則及使用量。正式估算前應以候選服務的最新價格及實際生成樣本重新計算。

## 6. 成功指標與驗證方式（Success Metrics & Success Criteria）

MVP v1.1 主要目的為驗證核心使用體驗及生成內容是否符合目標使用者需求，本階段暫不以規模化產品的使用次數及留存率作為主要成功標準。

### 6.1 使用者活躍度（Engagement）

初期透過小規模實際使用觀察使用者是否願意持續進行情境生成與口說練習。

- 使用者是否願意連續產生多組情境對話進行練習。
- 使用者在初次體驗後，是否願意再次使用產品。
- 使用者是否產生保存或重新查看過往對話的需求。

若後續驗證確實存在持續使用需求，再於後續版本加入使用次數、每週活躍度等量化指標。

### 6.3 留存率（Retention）

MVP v1.1 暫不設定 D7 留存率等量化目標。

本階段先透過小規模測試觀察使用者是否願意在不同日期再次使用 EcoSpeak。待產品具備歷史紀錄、使用者識別或分析機制後，再定義正式的留存率指標及統計方式。

### 6.4 核心價值驗證

除了使用意願，也應觀察生成內容是否真正符合產品欲解決的核心問題。

初步驗證方式：

- 生成的目標片語是否符合中高級英語學習者需求，而非過於基礎的日常表達。
- 生成的英文對話是否自然，且符合實際生活情境。
- 4–5 句的對話長度是否適合短時間閱讀及口說練習。
- 使用者能否在相似情境中主動使用目標片語。
- 使用者是否認為短篇情境對話比原本的練習方式更容易持續執行。
- 使用者是否產生保存或再次查看特定對話的需求。

此項可先透過實際生成樣本、小規模使用者測試及訪談驗證，不必在 MVP 階段立即開發自動化評分功能。

## 7. 待確認事項（Open Questions）

- **目標使用者：** 是否以多益 800 分以上的學習者為主要客群，或擴大至其他程度的英語學習者？
- **目標片語難度：** 如何讓生成的目標片語符合中高級學習者「能理解，但實際口說時不容易主動使用」的需求？
- **效能目標：** 文字生成的可接受等待時間為何？
- **成功指標：** 經過初期實際使用後，哪些行為最適合作為後續版本的量化成功指標？

以上事項可依課程進度、技術可行性及使用者測試結果逐步確認。
