# Design

## Context

動機見 proposal.md。現有服務將模型名稱直接傳入 Responses API builder；現有測試也固定斷言舊模型。補充範圍允許同步替換該測試斷言中的模型名稱。

## Goals / Non-Goals

**Goals:** 程式差異精確限於 `DialogueService.java` 與 `DialogueServiceTests.java` 各一處模型名稱字串。

**Non-Goals:** 不加入模型設定、fallback、SDK 更新或相容性修正；不修改其他測試邏輯或程式碼；不部署、不同步 baseline specs、不執行 Archive。

## Decisions

- 直接替換 `.model(...)` 的字串，使用需求指定的 `gpt-5.6-terra`。不抽取常數或設定，避免引入額外程式變更。
- 依補充範圍，僅同步替換既有測試的模型名稱斷言，保留其餘測試程式碼，執行服務測試並確認全部通過。
- baseline 已記載模型名稱，因此建立完整 MODIFIED requirement delta，僅換名稱。同步或歸檔不在本次範圍。

## Risks / Trade-offs

- [測試範圍擴大] → 以 diff 確認測試只變更模型名稱字串。
- [指定模型的服務端可用性及 Structured Outputs 相容性未驗證] → 不聲稱已驗證；不藉此更改參數或模型名稱。若後續執行發現問題，回報並停止擴大修改。

## Migration Plan

服務與測試各作一處模型字串替換，再檢查 diff 並確認既有服務測試通過。無資料遷移或部署工作；需要還原時將該字串改回 `gpt-5-mini`。
