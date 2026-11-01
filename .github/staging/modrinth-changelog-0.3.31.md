## Totem Nexus 0.3.31

- 所有原版已探索地圖都能直接開啟 Nexus 介面；中心、縮放與延伸範圍改由 MapId 的伺服器資料管理，不再依賴原始磁石仍然存在。
- 地圖只在開啟時依目前玩家、探索紀錄與權限投影可用節點；地圖物品本身不保存或洩漏節點清單。
- 以地圖點擊磁石仍可精確建立以該磁石為中心的 Nexus 地圖，並保留既有的擴張與地形資料。
- 製圖台加入「地圖 + 水桶」清洗：清除 Nexus 身分與既有地形資料，產出可重新探索的空白地圖並歸還空桶。
- 加強地圖開啟預檢、移除原節點後的獨立使用、傳送提交、原版製圖操作與存檔重載的伺服器權威驗證。

---

- Any explored vanilla map can open the Nexus interface. Center, scale, and extended coverage are managed by server-side MapId records and no longer require the original lodestone to remain.
- Available nodes are projected when the interface opens using the current player, discovery state, and permissions; map items do not store or leak node lists.
- Using a map on a lodestone still creates an exact lodestone-centered Nexus map while preserving expansion and terrain behavior.
- Cartography tables now cleanse a map with a water bucket, removing Nexus identity and explored terrain to produce a reusable blank map while returning an empty bucket.
- Adds server-authoritative regression coverage for open preflight, origin-independent maps, teleport commit, vanilla cartography operations, and save/reload behavior.
