## Totem Nexus 0.3.29

- 修正單人遊戲開啟 Space Unit 地圖時暫停整合伺服器，導致細節圖層無法生成、所有倍率只剩最粗略底圖的問題。
- 200%、400%、800%、1600% 現在會分別要求並顯示對應解析度；地圖畫面開啟時仍持續處理 LOD 與顏色封包。
- 新增整合式 Client GameTest，以真實擁有者請求、伺服器 LOD 生成與原版地圖色彩封包驗證四個倍率，不注入客戶端地圖資料。
- 非地圖型 Space Unit 介面仍維持原本的單人遊戲暫停行為。

---

- Fixes integrated singleplayer pausing while a Space Unit map is open, which prevented detail layers from being generated and left every zoom level on the coarsest base map.
- 200%, 400%, 800%, and 1600% now request and render their corresponding resolutions while LOD generation and color delivery continue with the map screen open.
- Adds an integrated Client GameTest covering real owner requests, server-side LOD generation, and vanilla map color packets at all four zoom levels without client map-data injection.
- Non-map Space Unit interfaces retain their existing singleplayer pause behavior.
