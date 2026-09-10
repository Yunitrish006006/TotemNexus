## Fixes

- Restore online mutual friends as player teleport destinations. Friend status and map coverage are rechecked by the server before teleporting.
- Use the clicked lodestone as the source when opening teleport with a compass on a lodestone. Using the compass in the air uses the player as the source.
- Clearly identify player and lodestone sources in the teleport interface.
- Allow array and build-site previews for a selected nearby lodestone without changing the player teleport source. Map-authorized previews remain valid while building and recheck painted coverage.
- Keep the Observer material page synchronized while preserving read-only controls.

## 修正

- 恢復在線好友玩家傳送，並由伺服器重新檢查好友關係與地圖範圍。
- 羅盤點擊磁石時，以該磁石為傳送來源；對空使用則以玩家為來源。
- 介面明確標示玩家或磁石來源。
- 可用選中的附近磁石預覽傳送陣與可建造區域，不會改變傳送來源；持地圖開啟的預覽會持續驗證已繪製範圍。
- 修正 Observer 材料頁同步並保持唯讀操作。

Minecraft 26.2 / Fabric. Requires Fabric API and TotemCore 0.7.19 or newer within 0.7.x.
