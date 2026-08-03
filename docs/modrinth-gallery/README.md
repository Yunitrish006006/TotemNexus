# Modrinth gallery

These screenshots are captured by the existing Nexus client GameTests on
Minecraft 26.2/Fabric with Java 25. Keeping the source images and their copy
here makes the Modrinth presentation reproducible.

## Trusted Space Unit friends

- File: `friends.png`
- Modrinth title: `Trusted Space Unit friends`
- Modrinth description: `Review trusted friends, incoming requests, and sent invitations before sharing access to a Space Unit teleport route.`
- 中文說明：在分享 Space Unit 傳送陣路線前，清楚檢視已信任玩家、收到的申請與已送出的邀請，避免把私有傳送網路錯誤公開。

## Teleport-array material diagnostics

- File: `material-diagnostics.png`
- Modrinth title: `Teleport-array material diagnostics`
- Modrinth description: `Inspect capacity, scan reach, stability, wear, routing, dimensional affinity, and maintenance targets for a lodestone teleport array.`
- 中文說明：檢視磁石傳送陣的容量、掃描距離、穩定度、耐久、路由、維度親和與維護目標，讓材料配置能被具體判讀。

Regenerate with:

```bash
JAVA_HOME=/path/to/java-25 xvfb-run -a ../TotemCore/gradlew runClientGameTest --no-daemon
```
