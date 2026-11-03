# Evidence ledger, hashes, and unknowns

This file records artifact identity, REA evidence IDs, key firmware addresses,
and the confidence level of each major claim.

## 1. Artifact hashes

| Artifact | SHA-256 |
|---|---|
| `binaries/K5.bin` | `39b057e19ca7e3252f29ef257f0f496d471b90c54450aa3f794cbc6d002cc88e` |
| `binaries/INNOVV_1.0.38.20260618_APKPure.xapk` | `de3689f7e74ec15743386b57c58b7092c4f0bb77f4f7c7cf9c904867a8a9560d` |
| base APK `cn.rxt.case.innovv.apk` | `efa532cefd6c64285fd712c24890f632e1d8f29b2f4d6ddeb77d0dea49ae4307` |
| decompressed RTOS `part2.dec.bin` | `88837f23a07467eb5157428be395d1c5affad47c543e85567a5fe075fb8d5395` |
| UBI partition 9 image | `9b1130dd5cd4ec30831ace5665efa8f3e3b08494dc469184e575ab971fe64232` |
| UBI partition 10 image | `f6615aba8f8833580648187dde7fe6f50b80e0957ba06155ec932b748486c064` |
| rootfs BusyBox | `9e79ab7d1b3a28bb75abf7add22c238313fe7726d980e229693590ddfb358290` |
| rootfs `libnvtlive555.so` | `d614d7d32eec2280bcdac9b33f392e48f4ec8f9a1ee76a507b94d62321213388` |
| K5 preference JSON (raw, with `//` comments) | `a744a3bcecc74e03e8a2f2a640ab7bfc3e64d2e17df5813d4661562d06b1cc8d` |
| K5 preference JSON (cleaned, valid JSON) | `c1d1a05d5ab4a5a2bdd3c21ecf8e720eeaf996ae0060aae31b2cb8399b89209b` |

## 2. Android REA evidence IDs

| Operation / subject | Evidence ID |
|---|---|
| `inspect_android_package` summary, base APK | `ev_e609c2fb680f1ad75fcbffae03316b746a0f35afc41d6dfc6e6e56b99e43de6b` (parent) |
| `inspect_android_class` `m.mifan.acase.ly.k` (LyProtocol) | `ev_643abf842b25e1413298f35b76fc39f1d375aea5835b87f5da6e192a7517dba6` |
| `inspect_android_class` `m.mifan.acase.core.d` (event socket) | `ev_5638adcb0c5331b615920e1d37b0312ce9c0f0b4d38c08bcd38f415430fa1aff` |
| `inspect_android_class` `m.mifan.acase.ly.h` (file list parser) | `ev_f685997d865b4c8f193d08fc905c85a843883927078dc5abe41e822291262f64` |
| `inspect_android_class` `m.mifan.acase.ly.l` (constants) | `ev_6745cbf7894e574c38701e32a11a1aedad7c3f99b7d5d43ff9ea3c699c9e51d1` |
| `inspect_android_class` `m.mifan.acase.ly.innovv.a` (INNOVV subclass) | `ev_1ec9a3e8cb29885f1438f189dbaf0490066653efc4b77cc3d0564bee6e2d14b0` |
| `inspect_android_class` `cn.rxt.ui.hyhHttp.a` (connectivity) | `ev_75a5791aae2db5c5bf192c4b02931c72e5a556666fde62a86a05e9bfe3a22963` |
| `procedure_pseudo_code` AutoTest handler | `ev_54b4d243a99a6e50ecc76f49c421b01aaf65623c1a5531f0fd542e15708d17fe` |

The base APK package summary (REA) reported:

* package `cn.rxt.case.innovv`
* version name `1.0.38.20260618`, version code `38`
* minSdk 21, targetSdk 35
* 9,096 classes, 620 resources
* permissions listed in [02-android-app-analysis.md](02-android-app-analysis.md)

## 3. Firmware REA evidence IDs

The firmware file was opened with REA as a raw ARM image at entry
`0x01800000` (`open_binary` with `format=raw-binary`,
`architecture=arm`). Its decompressed SHA-256 is in section 1.

| Operation / subject | Evidence ID |
|---|---|
| `search_strings` `custom=1&cmd=` | `ev_f133e795a9caa751bf7da936c3f2b253e4d50b42b77cf273156d839554e33ca9` |
| `xrefs` to `0x01E87658` (`custom=1&cmd=`) | `ev_ac0290c068e14fdc115188d336f3a9c2583794b3177899b5d021873a6c41d5e6` |
| `procedure_pseudo_code` dispatcher `0x01B9826C` | `ev_eeaf275321a78f126a85c6660b3ca4e3882774e064b9ea08ea62a3c1d1dcc6b1` |
| `xrefs` to command-table pointer `0x02102874` | `ev_e30614c8449dae050d5e50df5a76a8ac483d45c1b6f44381919945e816da317b` |
| `procedure_pseudo_code` cmd 2019 handler `0x01A8C134` | `ev_bc67a5b5e31b8df787213fc0a3d66b227ca1d242f6eecf8bb85540d6bd272652` |
| `procedure_pseudo_code` cmd 3012 handler `0x01A8D6AC` | `ev_d65b7c110a2369e28b1313146ee51a56ee5bd1d5d55d38b671ab82a2138e0d49` |
| `procedure_pseudo_code` cmd 3014 handler `0x01A8D89C` | `ev_b54c030db12ee5c015830ae323c0db25bcea46a54ffdf5b2dffd052f4a79f909` |
| `procedure_pseudo_code` cmd 3015 handler `0x01A8C244` | `ev_907b33a750c634cc883f01945070eb60d2e69cee125f3405471773bd3a3066cf` |
| `procedure_pseudo_code` cmd 3031 handler `0x01A8EA98` | `ev_a6d80db7dd0fce0e59d3375dd2723be000584794d90e12c2a6a4ed76e0f32ea8` |
| `procedure_pseudo_code` cmd 3037 handler `0x01A8E178` | `ev_dd01b55d1a545d33384ee9581cc2569d1dbb0542677f059044ad4d974464d34e` |
| `procedure_pseudo_code` cmd 4005 handler `0x01A8D42C` | `ev_2e2e9fc9ff3647354c0e3f80db77c6f543cf6fa2dd0b2f4e0074ba578a2b7379` |

## 4. Key firmware addresses in `work/part2.dec.bin`

| Address | Meaning |
|---|---|
| `0x01B9826C` | HTTP CGI dispatcher (`custom=1&cmd=` parser) |
| `0x01E87658` | Literal `custom=1&cmd=` |
| `0x01CC81A4` | Format `custom=1&cmd=%d&par=` |
| `0x01CC88F4` | Format `custom=1&cmd=3031&str=` |
| `0x01EE1228` | Command table (122 entries × 20 bytes) |
| `0x01EE20D0` | Menu table (27 entries × 12 bytes) |
| `0x02102874` | Global pointer to the command table |
| `0x01A8DE18` | Cmd 1001 handler (capture photo) |
| `0x01A8DAC4` | Cmd 1003 handler (free photo slots) |
| `0x01A8E02C` | Cmd 2016 handler (recording time) |
| `0x01A8CE58` | Cmd 2018 handler (raw JPEG) |
| `0x01A8C134` | Cmd 2019 handler (live-view links) |
| `0x01A8DB0C` | Cmd 2009 handler (max record time) |
| `0x01A8BE74` | Cmd 3002 handler (query commands) |
| `0x01A8D6AC` | Cmd 3012 handler (version) |
| `0x01A8D89C` | Cmd 3014 handler (current values) |
| `0x01A8C244` | Cmd 3015 handler (file list) |
| `0x01A8DA84` | Cmd 3016 handler (heartbeat) |
| `0x01A8DB54` | Cmd 3017 handler (free space) |
| `0x01A8DFB0` | Cmd 3019 handler (battery) |
| `0x01A8DFF8` | Cmd 3022 handler (hardware capability) |
| `0x01A8E0C4` | Cmd 3024 handler (card state) |
| `0x01A8E2B8` | Cmd 3025 handler (download URL) |
| `0x01A8E2E8` | Cmd 3026 handler (update path) |
| `0x01A8E81C` | Cmd 3029 handler (Wi-Fi info) |
| `0x01A8E8B8` | Cmd 3030 handler (resolutions) |
| `0x01A8EA98` | Cmd 3031 handler (menu) |
| `0x01A8EEA0` | Cmd 3034 handler (auto test) |
| `0x01A8E178` | Cmd 3037 handler (mode/status) |
| `0x01A8E39C` | Cmd 3038 handler (Wi-Fi scan) |
| `0x01A8C530` | Cmd 4001/4002 handler (thumbnail/preview) |
| `0x01A8DBA0` | Cmd 4003 handler (delete one) |
| `0x01A8DCFC` | Cmd 4004 handler (delete all) |
| `0x01A8D42C` | Cmd 4005 handler (movie info) |
| `0x01A8EF84` | Cmd 5001 handler (upload file) |
| `0x01A8F204` | Cmd 5002 handler (upload audio) |
| `0x01A8F5F0` | Cmd 8058 handler (GPS info) |
| `0x01A8F268` | Cmd 8228 handler (car number) |
| `0x01A8F564` | Cmd 8229 handler (custom string) |
| `0x01A8F2F4` | Cmd 8307 handler (GPS speed) |
| `0x01A8F3AC` | Cmd 8308 handler (GPS coordinates) |
| `0x01A8F468` | Cmd 8004 handler (GPS status) |
| `0x01A8F4AC` | Cmd 8315 handler (storage capacity) |
| `0x01A8F528` | Cmd 8008 handler (status flag) |
| `0x01A8D02C` | Cmd 8322 handler (front raw JPEG) |
| `0x01A8D1D0` | Cmd 8323 handler (rear raw JPEG) |

## 5. Confidence table

| Claim | Confidence | Basis |
|---|---|---|
| K5 uses Novatek CarDV HTTP API `custom=1&cmd=` | observed | dispatcher + command table + app strings |
| `custom=1` must be first | observed | dispatcher searches literal prefix |
| `3014` `<Status>` is the current value | observed | handler calls a getter and passes result as `%d` |
| `3015` file list schema | observed | firmware format strings + app parser |
| Direct file URL = `FPATH` with `A:` removed and `\`→`/` | observed | app `h.java` |
| Thumbnail = file URL + `?custom=1&cmd=4001` | observed | app `h.java` |
| Event socket port 3333 | observed | app `core.d` constructor + `LyProtocol` |
| RTSP/MJPEG URLs | observed (response fields), values inferred | firmware handler + app defaults |
| FTP/TFTP enabled | observed (rootfs config) | `etc/inetd.conf`, symlinks |
| FTP root/empty password | inferred | `/etc/passwd`; not tested |
| K5 JSON is the K5-specific settings definition | observed | `res/raw/ly_innovv_preferences_k5.json`; `LyCase.H` selects it when version contains `ik5` |
| Stock app logging BODY | observed | `core/r.java` |
| Stock app single-request dispatcher | observed | `core/r.java` (`maxRequests=1`) |
| `3015` may truncate | inferred | bounded buffer in handler |
| `ATTR` bit meanings | unknown | not decoded |
| `8008` exact meaning | unknown | app treats `Value==1` as true |
| `8315` Value/Value1 meaning | inferred | app `F0()`/`E0()` math |
| `3020` event status mapping | partly inferred | app `d0()` switch + constants |
| Firmware update preconditions | unknown | not tested |
| RTSP path/port on K5 | unknown | app default vs firmware string differ |
| Concurrent request behavior | unknown | not tested |
| Qz protocol details | out of scope | different camera family |

## 6. What was not analyzed

* Native libraries in `config.arm64_v8a.apk` (IJK, VLC, FFmpeg, ExoPlayer,
  OkHttp, Glide) beyond strings.
* The Linux kernel binary internals (only version and layout).
* The bootloader internals.
* The RTOS RTSP/streaming implementation beyond strings and handler addresses.
* Live traffic from a real K5 (camera was offline).
* The Qz protocol (`192.168.10.1:8082`) beyond confirming it is not the K5 path.
* Firmware update file format/signature.
* The exact SD-card filesystem layout on a real card (file list is authoritative).

## 7. How to extend this

* Capture a live `cmd=3014`, `cmd=3015`, `cmd=3031&str=all`, and `cmd=2019`
  response from the camera and add them to `data/` as golden samples.
* Test `cmd=3015` truncation on a full card.
* Test FTP/TFTP credentials and throughput.
* Test HTTP Range/resume.
* Test RTSP with `ffplay -rtsp_transport tcp`.
* Test the event socket under record start/stop, card insert/remove, and GPS
  changes.
* Verify the `3020` status codes against the app's `d0()` switch.
* Verify `8008`, `8315`, and `ATTR` semantics with targeted experiments.
