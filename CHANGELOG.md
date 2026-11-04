# Changelog

All notable changes to this project are documented here.

## Unreleased

- Add optional `InnovvTrafficLogger` and `InnovvConfig.trafficLogger` for
  debug request/response capture. Bodies are capped and credential responses
  are redacted.

## 0.1.0 — 2026-11-03

Initial SDK release.

- Kotlin Multiplatform targets: Android, iOS (`arm64`, `simulatorArm64`, `x64`),
  and JVM.
- K5 control API client with typed operations for identity, recording, photo,
  settings, media, live view, Wi-Fi, GPS, and events.
- Streaming media downloads and JPEG thumbnail/preview helpers.
- TCP `3333` event stream exposed as a `Flow`.
- Tolerant K5 XML parser for the non-single-root `3014` response.
- Explicit public API with KDoc, MIT license, Maven Publishing metadata, and an
  iOS static framework.
- Common `MockEngine`/parser tests.
