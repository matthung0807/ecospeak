# Proposal

## Why

將 EcoSpeak 對話生成使用的模型依需求從 `gpt-5-mini` 切換為指定字串 `gpt-5.6-terra`。本 change 僅規劃單一模型名稱替換，不主張模型品質、可用性或相容性已經驗證。

## What Changes

- 後續實作僅將 `DialogueService.java` 的 `.model("gpt-5-mini")` 改為 `.model("gpt-5.6-terra")`。
- Prompt、其他 API 參數、Structured Outputs、回應解析與錯誤處理全部保持原樣；不重構、格式化或優化。
- 補充範圍：同步更新測試預期的模型名稱，僅替換字串；不修改 baseline specs，不執行 Archive。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `dialogue-generation`：現有 Request structured AI output requirement 明載舊模型；delta 僅替換該模型名稱，其餘需求及情境保留。

## Impact

服務實作檔為 `src/main/java/com/matthung/ecospeak/service/DialogueService.java`，僅一處字串。無依賴、前端、API 契約或設定變更。

`src/test/java/com/matthung/ecospeak/service/DialogueServiceTests.java` 僅同步替換模型名稱斷言為 `gpt-5.6-terra`，不修改其他測試邏輯或程式碼；相關服務測試必須全部通過。
