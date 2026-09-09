## Why
死亡落點依賴死亡當下的座標與一般傳送偏移，背包移動後可能傳到錯誤位置。羅盤與地圖也被固定磁石八格限制擋住，無法作為陣外的低穩定度傳送工具。

## What Changes
- 死亡傳送追蹤綁定背包的即時維度與位置；傳送前再次確認同一背包仍有效。
- 落點優先距離背包 2–4 格，水平距離最多 6 格；優先可直接看到或短路徑步行抵達的位置，不再隨機丟到遠處。
- Nexus 新增單一「虛化」（Phasing）狀態效果，整合虛弱 I、夜視、隱形、抗性 I。效果註冊、邏輯、翻譯與 16×16 圖示全部歸 Nexus。
- 成功傳送到自己死亡背包附近後給予虛化；成功回收該背包後精確保留 60 ticks（3 秒）再結束。取消舊有固定 60 秒的搜尋期限。
- 羅盤、回收羅盤與地圖可在陣外傳送；來源是玩家實際位置。合法傳送陣的建築範圍提供穩定度加成，取代固定八格開啟限制。
- 保留目的地權限、地圖已繪製覆蓋限制、物品身分驗證、成本及安全落點檢查。陣外狀態本身不得成為禁止傳送的理由。

## Impact
- TotemNexus: death target resolution, safe landing, recovery lifecycle/effect, portable source authority, quotes, handbook, translation, native UI/Observer validation.
- TotemRemnant: publish movement of the actual bound death backpack and recovery lifecycle; no effect registration or effect mechanics.
- TotemCore: only if needed, a backwards-compatible default movement callback on the existing death lifecycle contract; no Nexus gameplay effect implementation.
- Existing movement/cost/session checks and read-only Observer ownership remain mandatory.
- Status: approved by the user with「開始吧」; implementation and required validation complete; see `test-artifacts/close-backpack-recovery/validation.md`.
