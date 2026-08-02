# TotemNexus

TotemNexus 是 Totem 系列的 Space Unit、好友、地圖、安全傳送、死亡節點
與分散出生點模組。所有座標、權限、成本與安全落點都由 Server 重新
驗證，Client 只顯示經過篩選的資訊。

目前候選版本為 **0.2.3**，精確搭配 TotemCore **0.4.0**。

## 安裝

Client 與 Server 都放入：

1. Fabric API `0.154.2+26.2`
2. TotemCore `0.4.0`
3. TotemNexus `0.2.3`

| 項目 | 需求 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3+ |
| Java | 25+ |
| 必要 Totem 模組 | `totem-core =0.4.0` |
| 選配 | TotemRemnant（死亡背包 ↔ Death Node 整合） |

Nexus standalone 不要求 DeadRecall、Remnant 或 Discord Bridge。使用
DeadRecall 2.4.7 整合 JAR 時不要再安裝獨立 TotemNexus。

## 快速開始

### 1. 註冊磁石

手持普通羅盤右鍵未註冊磁石。Server 會：

- 建立私有 `LODESTONE` Space Unit。
- 保存維度、座標、Owner、狀態與結構快照。
- 將節點 UUID 寫入羅盤，同時保留原版磁石羅盤指向效果。

若羅盤堆疊大於一，生存模式會拆出一個獨立的綁定羅盤。

### 2. 探索磁石

手持普通羅盤左鍵已註冊磁石。只有親自到達、有查看權限且節點仍有效
時，該節點才會加入玩家自己的永久探索資料。

### 3. 開啟地圖

- 右鍵已探索磁石：以該磁石為來源開啟地圖。
- 右鍵空氣：以玩家目前位置作為臨時來源。
- 選取節點後查看 Server 報價，再按「傳送」建立倒數 session。

## 介面物品

| 物品 | 能力 |
| --- | --- |
| 普通羅盤 | 完整功能：註冊、探索、好友、磁石管理與傳送 |
| 回生羅盤 | 傳送到自己的有效死亡節點時降低水平偏差 |
| 書本 | 前往固定磁石時縮短準備時間並降低石碑磨損 |
| 已繪製地圖 | 目標位於該 map 覆蓋範圍時降低食物成本與偏差 |

空白地圖不是有效介面。倒數期間必須持續在原使用手持有相同介面物品；
已繪製地圖還必須保持相同 map ID。

每次成功完成傳送後，實際使用的介面物品會成為玩家唯一的靈魂綁定傳送
物品；前一次使用的物品立即失效。只開啟地圖、查看報價、取消或失敗傳送
不會更換綁定。安裝 TotemRemnant 時，這一件物品會在死亡後保留，讓玩家
重生後仍能透過正常 Nexus 成本與安全規則前往死亡節點或其他目標。

## 石碑結構與材料

Nexus 從磁石周圍一格、排除中心的 `3×3×3`（26 格）開始掃描。帶有正
掃描擴張值的已掃描材料，會從**自身位置**繼續展開掃描；這會沿玩家擺放
的材料路徑延伸，而不是解鎖整個外殼。掃描只讀取已載入區塊，且離磁石最遠
不會超過 5 格。

Tier 改由材料提供的有效容量決定：

| 條件 | Tier |
| --- | ---: |
| 少於 8 容量 | 0 |
| 至少 8 容量 | 1 |
| 至少 24 容量 | 2 |

入門配置是在磁石同高度對稱放置一圈 8 個有效方塊：

```text
S S S
S L S
S S S
```

`L` 是磁石；`S` 是能提供容量的結構材料。石磚、深板岩、地獄磚、黑石、
銅、金屬、礦物、原礦塊與礦石皆有不同的正負取捨；銅還會受形狀、氧化與
上蠟狀態影響。Space Unit 地圖的「材料」頁才是目前資料包數值的權威來源；
資料包作者可參考 [材料 profile 文件](docs/teleport-array-material-profiles.md)。

跨維度傳送要求兩端固定磁石至少 Tier 1。

## 傳送成本與安全

- 同維度基礎食物成本約每 384 格增加 1 點，最高 20。
- 優先扣飽和度，再扣飢餓值，最後才消耗物品欄中的安全食物。
- 跨維度另外消耗紫水晶碎片；來源與目標的催化單位合計每 4 單位折抵 1，
  最終至少消耗 1。紫水晶方塊預設提供 1 單位，資料包可調整或提供負值。
- 創造模式不消耗資源。
- 穩定度低於 0.2、權限失效或沒有安全落點時不會傳送。

準備時間為 40–300 ticks。受傷、死亡、移動超過 4 格、切換維度、
換掉介面物品、來源／目標失效、資源不足或好友解除都會在扣款前取消。
材料還會由 Server 計入穩定、干擾、漂移、到達傷害、損耗、食物、準備時間、
冷卻、路線負載與維護成本，所有最終數值皆有安全上下限。

安全落點要求腳部與頭部無碰撞／流體、有可站立地板、位於世界邊界內，
並避開岩漿、火、營火、仙人掌、岩漿塊與細雪。

## 好友與玩家目標

- 普通羅盤右鍵玩家可送出或接受好友邀請。
- 只有雙向好友且對方在線時，玩家才會成為 `PLAYER` 傳送目標。
- 玩家地圖只收到好友的粗略位置與距離，不公開即時精確座標。
- 倒數期間解除好友、目標離線或死亡會立即取消傳送。

## 死亡節點

同時安裝 TotemRemnant 時，死亡背包可建立綁定的 `DEATH` Space Unit。
0.2.0 保存 Death Node → backpack Entity UUID 的反向連結，因此管理診斷
不需載入 chunk 搜尋實體。死亡背包完全回收後，對應節點會停用並從
一般地圖隱藏。

管理員指令：

```text
/deadrecall deathnodes
/deadrecall deathpoints
```

GUI 支援 Owner／維度／狀態／時間篩選、安全傳送、先停用後永久刪除，
以及需要二次確認的批次操作。Console 不能開啟 GUI。

## 跨模組事件

Nexus 透過 TotemCore event bus 發布公開 Space Unit 更新、死亡背包回收
與管理稽核摘要。TotemDiscordBridge 可自行訂閱這些型別事件；Nexus
不直接依賴 Discord，也不再呼叫 DeadRecall 的反射 adapter。沒有任何
subscriber 時，Nexus standalone 行為不受影響。

## 舊世界相容

Nexus 保留既有的四組 `deadrecall` SavedData keys、payload IDs 與資源
identifiers。0.2.3 保留 0.2.1 已通過的 root authority seed → external migrate → 第二
JVM verify，涵蓋 Space Unit、探索／最愛、好友、分散出生點與死亡背包
反向綁定。

## 開發與驗證

```bash
../TotemCore/gradlew build
```

Client 視覺測試：

```bash
../TotemCore/gradlew runClientGameTest
```

候選版已通過 44/44 required Fabric GameTests、Dedicated Server、legacy
SavedData migration 與 Client 視覺 gate。測試截圖在
[`test-artifacts/screenshots/`](test-artifacts/screenshots/)；所有權契約見
[EXTRACTION.md](EXTRACTION.md)，migration 設計見
[AUTHORITY_MIGRATION.md](AUTHORITY_MIGRATION.md)。
