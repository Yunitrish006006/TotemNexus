## Context

Nexus owns four Observer-visible UI variants; TotemVanillaTweaks must coordinate transport without reproducing their visuals.

## Goals / Non-Goals

- Goals: reuse production screens, enforce read-only semantics and remain framebuffer-free.
- Non-goals: grant target administration or teleport authority to the viewer.

## Decisions

- Nexus exposes lazy client providers through TotemCore for map, friends, registration and death-node administration variants.
- Provider compatibility is explicit and bounded; missing/incompatible providers yield an unsupported state.
- Observer mode prevents init/tick/removed/widget callbacks from sending mutation or refresh requests.

## Risks / Trade-offs

- Some screens need observer-safe construction data; codecs remain bounded and server-authorised.

## Migration Plan

Ship TotemCore contract, Nexus providers, then VanillaTweaks dispatch.
