# Tasks

## 1. 模型字串替換與驗證（原範圍，已完成）

- [x] 1.1 僅在 `src/main/java/com/matthung/ecospeak/service/DialogueService.java` 將 `.model("gpt-5-mini")` 替換為 `.model("gpt-5.6-terra")`；以 `git diff -- src/main/java/com/matthung/ecospeak/service/DialogueService.java` 確認差異只有該字串，Prompt、其他 API 參數及 Structured Outputs 均未變動。
- [x] 1.2 執行 `./mvnw.cmd -Dtest=DialogueServiceTests test` 並回報結果；既有測試固定期待舊模型，預期模型斷言失敗，須如實記錄，不修改測試、不略過或宣稱測試通過。若出現其他失敗，也須回報。
- [x] 1.3 以 `git diff --name-only` 及 `git status --short` 確認除本 change 規劃文件與任務狀態外，唯一實作變更為上述服務檔；baseline specs、測試、設定及其他檔案均未修改，且沒有格式化或重構。

## 2. 同步測試模型名稱（補充範圍）

本節取代原範圍中保留舊模型測試斷言的限制；第 1 節保留先前完成的驗證紀錄。

- [x] 2.1 僅將 `DialogueServiceTests.java` 預期的模型名稱由 `gpt-5-mini` 改為 `gpt-5.6-terra`；以 diff 確認測試檔只有這一處字串變更，其他測試邏輯與程式碼不變。
- [x] 2.2 執行 `mvn.cmd -Dtest=DialogueServiceTests test`，確認相關測試全部通過，並回報結果；不執行 Archive。
