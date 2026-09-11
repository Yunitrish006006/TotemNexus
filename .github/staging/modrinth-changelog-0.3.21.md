## Changes

- Restore saved historical vanilla-map detail at power-of-two zoom levels, bounded by scale 0. Maps without recorded ancestry retain their current terrain fallback.
- Keep terrain, destination markers, panning and click selection aligned.
- Show the owning player's transient position marker without persisting player tracking on the map.
- Use Nexus Observer protocol 4 through the separate TotemObserver runtime. Keep map pixels and player position out of semantic snapshots, and suppress the observer client's own player marker.

## 更新

- 地圖以 1×、2×、4× 等倍率還原已保存的歷史細節，最細以 scale 0 為限；沒有祖先資料的地圖保留現有地形。
- 地形、目的地標記、平移與滑鼠點選維持對齊。
- 顯示使用者自己的即時玩家標記，不將玩家追蹤寫入地圖存檔。
- Nexus Observer 升至 protocol 4，搭配獨立 TotemObserver；語意快照不傳地圖像素或玩家位置，也不顯示觀看者自己的位置標記。

Minecraft 26.2 / Fabric. Requires Fabric API and TotemCore >=0.7.20 <0.8.0. Target and observer clients must use matching Nexus Observer protocols.

Access management now searches previously joined players, including offline players, using Core 0.7.20 shared directory. UUID-based grants and revocations retain server authorization. The new read-only Observer access screen excludes private search input.
