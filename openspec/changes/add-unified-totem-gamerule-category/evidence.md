## Coordinated release evidence

- TotemCore 0.7.15 is published from release-marker commit
  `84bbfc96f3aeabbbd9047fa8ae900b92e415f00f` (Modrinth version
  `McmiQ7jI`).
- Locksmith 0.1.7 was clean-built against the released Core JAR; its unit
  suites report no failures or errors and all 20 required Server GameTests
  passed.
- Remnant 0.2.17 was clean-built against the released Core JAR; its unit
  suites report no failures or errors and all 49 required Server GameTests
  passed.
- Nexus 0.3.10 was clean-built against the released Core JAR; its unit suites
  report no failures or errors and all 79 required Server GameTests passed.
- The three release artifacts declare Minecraft `~26.2` and
  `totem-core >=0.7.15 <0.8.0`. Deterministic JAR inspection produced these
  SHA-512 values:
  - Locksmith 0.1.7:
    `250c6b91e64d00c3042047183a232a2ce512ccd3b25b73e1e9056073656b98dd2765e5be6dee89754031bf9a7f76a147a66a1c2216fada33665696a7a7cbe742`
  - Remnant 0.2.17:
    `8a64db345e7b7361143c6fb9bb986aed1dc3313cf12bab9d8d5f404e67c30aa93ee807355bce40db8533cde7cf645449e3694475a2e3e46e5f7a9bd5eaa81092`
  - Nexus 0.3.10:
    `205c1949053c72f05df7a7a9c3a6f2ec1853a8a2f2ae533f670d414b72fc6884a372ea2922510a527d63d72226866a9a2353b4922bc3f63ed6923f6c85820d4a`
