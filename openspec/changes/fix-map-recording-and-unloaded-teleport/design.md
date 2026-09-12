# 設計與原始碼診斷

## 已確認與尚未確認

| 問題 | 原始碼證據 | 判斷 |
| --- | --- | --- |
| 資料像是跟玩家走 | `NexusMapBindingSavedData` 的鍵為 map_id；地形是 vanilla MapItemSavedData。`NexusSpaceUnitAuthority.visibleDiscoveredUnits` 對地圖採繪製範圍，`NexusInterfaceAccess` 對指南針採玩家探索 | 不能將地形誤判成存在玩家檔。需兩人交換與跨地圖隔離重現；個人最愛、權限不同仍會讓介面不同 |
| 路徑不一致 | `NexusMapPayloadAuthority.sendCalculated/send` 仍先用 visibleDiscoveredUnits；`NexusMapCutover` 註明正式互動由 NexusSpaceUnitAuthority 持有 | 替代入口有玩家探索先行過濾，不能當作已證實的正式服根因；應共用端點候選／授權政策，避免測試與正式行為分歧 |
| 手持玩家標記 | `NexusMapLifecycleAuthority.exactData` 傳入 trackingPosition=false；`NexusMapDetailScreenMixin.totem$drawLocalPlayer` 只補在展開 Screen | 手持路徑沒有同等補畫；不能只重跑現有 Screen marker 測試就宣稱修好 |
| 新區域沒有細節 | binding 只記最多四個 detailAncestors；`NexusMapDetailNetworking` 只傳同中心舊 MapId；Screen 只疊祖先較小範圍 | 是目前歷史細節方案的功能上限，沒有外圈採集流程 |
| 未載入目的地失效 | `NexusSpaceUnitAuthority.resolveTeleportTarget` 約 2008 行直接拒絕 !isLoaded；blame 指向 8d1e6bbe（2026-09-08） | 靜態路徑可確認在安全落點搜尋前被拒絕。現存 `NexusSafeLanding.Search` 仍有 addTicketAndLoadWithRadius 與 close 釋放 |

舊 `openspec/project.md` 的 Core 0.5.0／namespace 說明已落後；本次使用實際 source 與 Build 固定的 Core 0.7.21，不以舊敘述回退程式。

## 1. 地圖身分與交接規則

資料實體仍放在世界 SavedData，物品透過 MapId 指向它；不把大型圖資塞進物品 NBT，也不依持有者 UUID 選地形。

- A 將地圖 M 交給 B：M 的地形、細節、縮放歷史完全保留。B 的私人權限不會因拿到地圖自動提升。
- 同 MapId 的原版複製品共享後續探索更新，與原版地圖複製語意一致。
- 新建立的另一張地圖 N 不因同持有者或同磁石而自動繼承 M 的圖資。
- SCALE 產生新 MapId，保留原圖當下的已知資料與可證明祖先；新增探索寫入新圖的細節集合，不擴大舊圖範圍。
- LOCK 凍結基礎像素與細節版本；其他未鎖定複製／衍生圖後續採集不改動鎖定結果。
- 指南針的個人探索、最愛、好友、管理權限保持個人資料。地圖可顯示的節點採「該圖已繪製位置 ∩ 當前玩家權限」，不能先用個人探索剔除。
- 所有開圖、列表、預覽、傳送與替代 authority 共用上述規則；測試直接走正式註冊入口。

## 2. 手持玩家標記

保留 trackingPosition=false，避免直接開啟 vanilla 共享持圖者追蹤而改變位置隱私。新增僅針對服務端認可 Nexus 地圖的手持渲染整合：在實際物品地圖 render state 上附加暫態 PLAYER decoration，使用 Mojang 圖示與原版座標／旋轉語意。

Screen 與手持共用座標轉換：dimension、centerX/Z、1<<scale、負座標與邊緣處理；縮放／平移只套一次。主手、副手、單手／雙手姿勢各驗證；框架地圖不畫本機玩家；跨維度不畫錯誤位置；邊界依原版 off-map 規則顯示。每個 render state 僅一個本機圖示，不修改 MapItemSavedData.decorations 或永久物品元件。

Observer 禁止拿觀看者的 Minecraft.player 補畫被觀看者位置。本階段不新增遠端玩家座標分享；延續既有不轉送 owner 位置的政策。

## 3. 新區域的細節採集

採用「MapId 擁有的稀疏細節分頁」，取代只能靠祖先的限制；保留原版 128×128 主圖、MapRenderer 與 vanilla map 更新協定。

- 每張有效地圖索引其已採集 128×128 方塊的 scale-0 細節頁。每頁具獨立服務端 MapId、世界原點與版本；分頁是圖資，沒有傳送介面綁定身分。
- 玩家持有效未鎖定 Nexus 地圖探索時，在既有合法繪圖範圍中逐步採集已載入地形；即使沒有任何 detailAncestors，也能新增頁面。地圖在背包其他位置是否更新，沿用現有原版有效繪圖觸發條件，不另擴大揭露範圍。
- 採集保留 vanilla 地圖色彩、地形明暗、水深與維度規則；實作先核對 26.2 本機映射 source，不能直接呼叫可能同步載入區塊的 update 來假裝非阻塞。
- 使用已繪製／有效像素遮罩；整頁建立不代表整頁已探索。未知細節透明回退到主圖，禁止祖先空白覆蓋已有底圖。
- 客戶端依視窗只取相交頁面，以最近鄰與整數倍率呈現。主圖先畫、細節後畫、圖示最後只畫一次；中間縮放可用由已知像素導出的快取 LOD，但不得因此發明探索資料。
- 縮放上限不再由是否存在祖先決定；可放大查看，新探索處有細節，尚未採集處仍顯示粗圖。
- 初始可調預算：全服每 tick 至多 2048 個候選 column、每 MapId 每 tick 至多 128；輪替避免某人獨占。只查已載入 chunk，地圖採集不申請載入票據。
- 傳輸驗證持有 MapId、binding、維度、視窗、版本；每玩家每秒最多 4 次頁面請求、每 tick 最多 32 KiB 增量。最多 20 個活動圖層（包含最多 4 個歷史圖層），先粗圖再逐頁補齊；相同請求合併、超額延後。
- 最大 scale 4 為 2048×2048 方塊；任意中心與固定分頁格交錯時最多 17×17=289 頁，不能誤算只要 256 頁。顏色陣列上限約 4.52 MiB／全繪製地圖，另計索引／遮罩／儲存開銷；按需建立；首版以全世界 8192 頁上限控制記憶體，後續依實测評估磁碟冷頁。伺服器總快取與佇列也需固定上限，不能僅限制單張圖。

SCALE／LOCK 共用不可變細節頁版本，新資料 copy-on-write。此規則也涵蓋舊 detailAncestors：操作當下將祖先的已知像素與遮罩快照化，衍生圖不得繼續讀可變祖先 MapId。舊未鎖圖第一次遷移可匯入當下可驗證的祖先資料，但不得冒稱那是過去擴張當下的快照。舊鎖圖若無可證明的凍結細節版本，只保留自身鎖定主圖，停用可變祖先覆蓋；不能從現在的祖先推算過去鎖定狀態。實作將索引與頁面放進同一個版本化 SavedData，避免兩個獨立檔案的提交落差；舊頁保留；重啟時缺頁退回粗圖且不刪原記錄。垃圾回收須以完整引用索引為依據，首版不在遷移時刪頁。

舊圖遷移：沒有新索引時建立空索引，依上述快照規則匯入可驗證祖先。只有過去真的記錄過的高解析像素才可匯入；同磁石其他地圖不能猜成祖先。舊外圈若只存粗圖，重新走到該區即可逐步補細；不承諾從粗像素還原已丟失資訊，也不為升級全服掃世界。遷移具版本與完成標記、可重跑，保留舊資料；回退只停用新索引，原 MapId 仍可讀。

Observer 的頁面清單／LOD 屬 Nexus 自有 provider 的版本化語意，不傳截圖。實作須確認觀看端取得經授權 vanilla MapId 更新的實際路徑，不能假設傳 ID 就有紋理，也不能把 MapId 當授權 token。若現有 Core API 無法承載，先另列最小契約變更與消費者影響，不能繞到自製像素串流。缺資料保持原版粗圖與明確未就緒狀態；模組缺席保持 unsupported metadata。

## 4. 未載入目的地的傳送流程

採明確狀態：檢查授權 → 準備／載入目的地 → 完整重驗 → 安全落點 → 最終重驗與扣款 → 傳送／清理。

1. 列表、開圖、報價只用持久化端點快照，不為瀏覽載入區塊。維度不存在、節點已刪除／停用、失去權限仍立即拒絕。未載入是「待驗證」，不是失效，也不能寫成 disabled。
2. 正式開始傳送才取得既有 route reservation 和有期限的載入資源。延用 NexusSafeLanding 的 ticket/future/cleanup，將端點載入準備提到實體驗證前。不能只刪除 isLoaded 條件後讓 getBlockState 同步載入。
3. 載入期間不執行依賴實體方塊的否決檢查；每 tick 仍驗證玩家／介面／權限／取消條件。載入完成後確認磁石與必要結構掃描範圍都 ready，再重算結構、容量、報價。邊界結構若不完整必須等待有界必要 chunk，而非用半套結構當新真相。
4. 報價在載入前為暫估，不能提前扣款。載入後若成本／準備時間／偏差變更，更新報價並要求重新開始，避免默默增加費用；清理舊 reservation/ticket。完全相同才續行。
5. 保留目前 200 server tick 搜尋／載入上限；載入開始時設定該次嘗試的絕對 deadline，重新搜尋不重置；原有準備倒數另計，等待載入不消耗尚未開始的準備倒數。ticket 範圍由既有安全搜尋與最大結構範圍決定且有上限，不永久 force-load。
6. 真正載入後找不到磁石才標記失效；載入逾時、失去權限、沒有安全落點分別使用正確訊息。取消、死亡、離線、換手、換圖、停服、成功與失敗都釋放票據及路由。
7. 最終扣款／傳送前再查端點、物品身分、資源與權限；成功僅扣一次，失敗不消耗。好友玩家與死亡背包仍走各自權威來源，不把磁石判斷套到它們上面。

## 驗證與執行順序

先修未載入傳送，再補手持標記，接著統一地圖身分／交接與細節分頁，最後做遷移與相容驗收。每階段具獨立可判定測試，避免把整套大改一起定位。

固定程式：兩玩家交換、同 MapId 複製、新 MapId 隔離、SCALE/LOCK、save/reload、遷移重跑、負座標分頁、無祖先外圈採集、未知遮罩、請求／記憶體預算、未載入目的地、載入後磁石缺失、報價變更、移動取消、逾時與 ticket 清理。未載入測試須先斷言 chunk 不在已載入狀態，不能只使用 GameTest 出生附近。

圖形程式：實際主／副手／雙手 held-item render + Screen 的玩家圖示位置、旋轉、數量與不同 scale；框架／異維度／Observer 不冒出本機標記。新外圈放大、地圖交接、GUI scale 與繁中截圖。

既有 `.github/workflows/build.yml` 負責 unit/server/client GameTests；Nexus 本機最小失敗重現只在必要時執行。Observer 三 JVM E2E 與 Production Runtime 沿用 owning 驗證流程／固定腳本，缺少自動入口再補進既有 workflow。只人工看受影響圖片，相同 SHA 與設定的成功結果不重跑。實作與實際驗證結果記錄於 evidence.md；尚未通過的項目不視為完成。
