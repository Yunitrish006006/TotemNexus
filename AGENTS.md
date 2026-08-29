<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

## Module-owned Observer UI

- Every new or modified player-facing `Screen`/`Menu` must expose a module-owned,
  read-only semantic Observer mode through the TotemCore provider contract.
- TotemVanillaTweaks coordinates sessions, relay, target display identity and
  remote cursor only. It must never copy this module's renderer or draw a
  hand-drawn replacement screen.
- Observer rendering is permanently framebuffer-free: never transmit a
  screenshot, framebuffer, video or pixel stream.
- Observer mode must suppress slot, button, edit, drag, scroll and keyboard
  mutation paths, lifecycle requests and action packets. Escape may only stop
  observing; viewer authority never becomes target authority.
- Never relay secrets, tokens, passwords, API keys, prompts or unsent text.
- UI changes require unit coverage, native-scale Client GameTest screenshots,
  dedicated three-JVM E2E and Production Runtime validation. Do not update a
  screenshot baseline merely to conceal a visual regression.
- Provider capture/create and handle methods are client-thread-only; GameTests
  must use their client-thread context helpers.
