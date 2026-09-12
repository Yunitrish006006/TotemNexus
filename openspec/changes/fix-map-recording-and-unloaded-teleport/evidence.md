# 實作驗收紀錄

2026-09-12。範圍：Nexus 地圖記錄、手持位置標記、無祖先外圈細節、未載入磁石傳送，以及 Observer 的對應地形提供路徑。既有 Observer 帳號／Bridge 工作未回退。

## 實作

- 地形仍以原版 MapId 為基礎；新增世界 SavedData `totem:nexus_map_detail_v1`，同檔保存索引與細節頁。地圖轉交不依赖接手者的羅盤探索紀錄；權限、好友、收藏仍個人化。
- 同 MapId 共享更新；SCALE/LOCK 使用 copy-on-write 快照。擴張主圖保留原有已繪製範圍。舊未鎖圖只匯入可驗證祖先，舊鎖圖不匯入可變祖先；缺頁回退主圖。
- 手持標記注入 ItemInHandRenderer 的原版地圖渲染路徑，與 Screen 共用座標／方向轉換，未修改物品展示框路徑。
- 新區域以已繪製範圍與已載入區塊為採集門檻，依世界座標分頁。全服每 tick 2048 候選欄、每 MapId 128；超出當次預算的活動地圖保留游標。
- 地圖 v2 封包傳 geometry/identity，地形仍走原版 map packet。每收件者佇列 32 KiB/tick，最多 20 相交圖層；歷史層先畫，新細節後畫。客戶端最多 64 個細節頁、16 個 map owner 記錄。
- Nexus Observer provider 5；Observer 只在合法的同 family/variant/protocol 觀察 session 下請求 Nexus 伺服端提供地形。每次發送前重验持圖與觀察權限，無 framebuffer 或 renderer 複製。
- 磁石未載入不再當作失效；開始傳送才取得請求專用載入票據，載入後重驗實體、結構與報價。載入／安全落點共用 200 tick 預算，準備倒數另計；成功、取消與失敗皆清理。扣款／傳送失敗恢復槽位原物品及 components、飢餓與飽和。

## 已執行的固定驗證

| 範圍 | 結果 | 本機證據 |
| --- | --- | --- |
| Nexus unit | 95 tests，0 failures/errors/skips | `build/test-results/test/` |
| Nexus server | 24 required GameTests 全通過，包含真正卸載後重新載入、地圖轉交、快照、負座標、reload、退款 | `build/nexus-isolated-runtime-validation.log` |
| Nexus client | 三個受影響 test classes 全通過；雙手／主手／副手、400% 新外圈、provider lifecycle/input/cursor | `build/nexus-client-validation.log` |
| Nexus production | 實際 JAR 的同三個 client classes 全通過 | `build/nexus-production-validation.log`, `run/screenshots/` |
| Observer protocol | 4 unit tests 通過，保留 v3/v4 並接受 v5 | `../TotemObserver/build/nexus-relay-validation.log` |
| Observer dedicated E2E | 伺服器 + Target + Observer 三個 JVM 全通過；Nexus 400% 顯示真實 sparse detail，其他 owner families、unsupported metadata、Stop/cleanup 同時通過 | `../TotemObserver/build/nexus-e2e-validation.log`, `build/e2e/results/` |

實際看過雙手箭頭、新外圈細節與 Observer 地形畫面。新增驗收圖片位於 `test-artifacts/screenshots/map-recording-regression/`；未更新其他功能既有截圖基準。

`evidence.json` 保存 source HEAD、42 個實作／測試／設定檔 SHA-256、日誌 SHA-256 與 Nexus/Core/跨模組相依 JAR SHA-512。Nexus JAR 內 309 個 production class 已逐一與目前 main/client 編譯結果比對一致。工作區尚未提交，因此不能將 HEAD 單獨當成本次程式的 source 身分。

## CI 與證據沿用

- 既有 Build workflow 不帶 focus，繼續執行完整 unit/server/client 清單；新增 `-PnexusTestFocus=map runProductionClientGameTest` 與報告／截圖 artifact。
- 本機僅重跑失敗或輸入變更範圍。本次未推送，沒有宣稱 GitHub Actions 已執行本次變更。
- 開發 runtime 的 named Fabric API 檔案現在只寫入 `build/named-runtime-api` 的 SHA256 副本，classpath 在 Loom finalize 前替換；共享 Gradle cache 不再遭改寫，production 使用官方原件。
- 本機既有 cache 曾被舊程序改寫，因此這次用 Observer build 內由已驗證 official artifacts 建立的 Maven 隔離來源驗證；新的隔離 dev 流程也已實跑上述 24 tests。
- 早期 fixture 的全新遠端地形生成／GameTest 加速 tick 問題已改為 setup 預生成、確認實際卸載，再以正式非同步流程測試；正式遊戲的期限未放寬。被替代的失敗 run 不作通過證據。

## 限制

- 舊粗圖無法還原從未保存的細節，需重新到訪；世界細節頁上限 8192、每圖 293，達限保留現有資料並回退粗圖，未導入自動 GC。
- 採集與傳輸預算由固定上限及 loaded-only 路徑控制；本次未做長時間大量玩家壓力測試，不宣稱 TPS 或頻寬實測保證。
- 保留既有 owning-module-absent client coverage；本次三 JVM 包含所有相依 owner，未另跑完整缺席矩陣。
- 無 Core API 變更；未提交、推送、發布或修改使用者實際遊玩世界。
