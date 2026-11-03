# Replacement client design and performance notes

The goal is a client that feels fast on the same hardware and network. The
camera's CPU and HTTP server are the bottleneck; the stock app makes it worse
with client-side choices. The biggest wins are:

1. **Do not use a BODY-logging HTTP client.**
2. **Do not serialise every request behind a single-request dispatcher.**
3. **Cache `cmd=3014` and `cmd=3031`; do not re-fetch them per screen.**
4. **Use the TCP 3333 event socket for state instead of polling.**
5. **Download media directly; try FTP for bulk sync.**
6. **Play RTSP directly; avoid an HTTP caching proxy for live view.**

## 1. Recommended architecture

```
+-----------------------------+
| UI / app logic              |
+--------------+--------------+
               |
+--------------v--------------+
| CameraClient                |
|  - ControlApi (HTTP)        |
|  - EventStream (TCP 3333)   |
|  - MediaApi (HTTP/FTP)      |
|  - LiveView (RTSP/MJPEG)    |
+--------------+--------------+
               |
+--------------v--------------+
| Connection manager          |
|  - Wi-Fi bind               |
|  - reachability             |
|  - reconnect/backoff        |
+-----------------------------+
```

### ControlApi

* One HTTP client with:
  * connection pool enabled,
  * **no logging interceptor** (or headers-only in debug),
  * 3–5 s connect/read timeouts for control,
  * keep-alive.
* A small request queue (max 2–4 concurrent) or a single worker for control
  commands. The camera's RTOS server may serialise anyway.
* Parse XML with a real parser (not string splitting). Be tolerant of the
  per-file `<ALLFile>` wrapper and the `3014` status-as-value quirk.
* Retry idempotent reads (3014/3015/3016) once on timeout; do not blindly retry
  destructive commands (3010/3011/4003/4004).

### EventStream

* Connect to `192.168.1.254:3333` after the control heartbeat succeeds.
* Buffer bytes until `</Function>`; parse each complete message.
* Dispatch events to the UI.
* Reconnect with exponential backoff (1 s, 2 s, 5 s, 10 s, max 30 s).
* Do not open a new event socket per screen.

### MediaApi

* Fetch `3015` once per sync or when the user refreshes.
* Cache `(name, size, timecode, path)` locally.
* Download via HTTP or FTP.
* Use `.part` files, verify size, then rename.
* Delete from camera only after verification (and only if the user asked).

### LiveView

* Get URLs from `2019`.
* Use RTSP/TCP for movie mode; use an MJPEG parser for the HTTP stream.
* Keep one session per view; stop it when the view is not visible.
* Do not route the stream through an HTTP cache/proxy.

## 2. What the stock app does that you should not

| Stock behavior | Why it hurts | Replacement |
|---|---|---|
| OkHttp `HttpLoggingInterceptor` at `BODY` | Logs full bodies of every control response | Disable or `HEADERS` only in debug |
| `maxRequests = 1` dispatcher | Serialises all control requests | Allow 2–4 or use a small queue |
| `3014` on every settings/preview refresh | Full dump of all settings | Cache and update deltas |
| `3031&str=all` on every settings open | Large menu XML | Cache menu for the session |
| 1 s `HttpURLConnection` heartbeat | False timeouts, retries | 3–5 s timeout, 5–10 s interval |
| 500-byte event reads | Split/merged messages | Buffer until `</Function>` |
| AndroidVideoCache for playback | Extra proxy/disk I/O | Direct RTSP |
| Fetch all files and filter | Large XML/parse | Cache list, filter locally, try FTP |
| `System.out`/logcat everywhere | CPU/I/O overhead | Structured, rate-limited logging |

## 3. Suggested request policy

| Operation | Timeout | Retry | Notes |
|---|---:|---|---|
| Heartbeat 3016 | 2–3 s | 1 | Use for connectivity only |
| 3014 full dump | 5 s | 1 | Cache; do not call often |
| 3031 menu | 5 s | 1 | Cache for session |
| 2001 record start/stop | 5 s | 0 | User-visible action; don't double-toggle |
| Setting set + 3021 | 5 s | 0 | Verify with 3014 or local state |
| 3015 file list | 10–15 s | 1 | May be large/truncated |
| File download | 30–60 s read | 1 with Range | Use `.part` |
| RTSP | player-specific | reconnect | TCP transport |

## 4. Fast startup sequence

1. Connect to the camera Wi-Fi (Android `WifiNetworkSpecifier` / iOS NEHotspot;
   desktop: normal Wi-Fi).
2. `GET /?custom=1&cmd=3016` — confirm reachability (2–3 s timeout).
3. Start the event socket to `:3333`.
4. `GET /?custom=1&cmd=3012` — firmware version (for feature gates).
5. `GET /?custom=1&cmd=3014` — current settings (cache).
6. `GET /?custom=1&cmd=3031&str=all` — menu (cache).
7. `GET /?custom=1&cmd=2019` — live URLs (cache).
8. Show the preview.

Do not block startup on `3015`; fetch it in the background when the album opens.

## 5. Feature gating by firmware version

The app branches on the model/version string from `cmd=3012` (`ik5`, `ik6`,
`ik7`, `k2`, `k3`, `c5`). For K5, the RTOS version is
`IK5.5G.20210827.V1.1.12`. A replacement can:

* read `3012` once and store it;
* enable K5-specific settings (8006/8007/8010/8200/8201/8206/8210/8219/8304/
  8306/8309/8315/8318/8320/8321) only when the version/model matches;
* fall back to the generic Novatek command set otherwise.

## 6. Media sync algorithm

```
remote = parse(3015)
local  = scan(local_dir)
for f in remote:
    if f.name not in local or local.size != f.size:
        download(f)
        verify size
        rename .part -> final
for f in local:
    if f.name not in remote and user_wants_mirror:
        optionally delete local
```

Guidelines:

* Use `TIMECODE` + `SIZE` as the identity, not just the name.
* Keep a small SQLite/JSON index of downloaded files.
* Download in chronological order (oldest first) or newest first depending on
  user preference.
* Never delete from the camera unless the user explicitly enables it and the
  local copy is verified.
* If the file list is truncated, fall back to FTP `LIST`/`NLST` or the HTML
  directory listing.

## 7. Live view notes

* RTSP over TCP is more reliable on congested Wi-Fi.
* Low-latency settings: disable buffering, small probe size, drop frames if
  behind.
* If the stream stalls, reconnect the RTSP session rather than tearing down the
  whole app.
* The camera may only support one or two simultaneous RTSP clients. Test.
* For photo mode, use the MJPEG stream from `2019`; parse JPEG frames.

## 8. Error handling and reconnect

| Symptom | Likely cause | Action |
|---|---|---|
| HTTP timeout | Camera busy / weak Wi-Fi | Retry reads; reduce concurrency |
| `-256` | Command not supported in this mode/build | Hide/disable the feature |
| `-2`/`-4` | No card / file error | Show card state; refresh 3024 |
| `-7`/`-8`/`-9` | Recording storage/write/slow | Stop recording, show error |
| `3015` parse error | Truncated XML | Merge partial; try FTP |
| Event socket disconnect | Camera reboot/Wi-Fi drop | Reconnect with backoff |
| RTSP 404/timeout | Wrong path/mode | Re-read `2019`, retry |

## 9. Security hardening for the replacement

* Change the default AP SSID/password via `3003`/`3004` + `3021`.
* Do not log `cmd=3029` responses (plaintext credentials).
* Restrict the app to the camera's Wi-Fi network; do not expose the API to the
  internet.
* Treat destructive commands (`3010`, `3011`, `4003`, `4004`, `3013`) behind
  explicit confirmations.
* Be aware that FTP/TFTP are unauthenticated/weak; prefer HTTP if the camera is
  on an untrusted network (it is an AP, so the network is local).

## 10. Test plan

Static analysis cannot replace a real device. Verify in this order:

1. Heartbeat and version:
   `curl 'http://192.168.1.254/?custom=1&cmd=3016'`,
   `curl 'http://192.168.1.254/?custom=1&cmd=3012'`.
2. Settings dump/menu:
   `curl 'http://192.168.1.254/?custom=1&cmd=3014'`,
   `curl 'http://192.168.1.254/?custom=1&cmd=3031&str=all'`.
3. File list:
   `curl 'http://192.168.1.254/?custom=1&cmd=3015'`.
4. Direct download of one file and size check.
5. Thumbnail:
   `curl -o t.jpg '<file-url>?custom=1&cmd=4001'`.
6. Live URLs:
   `curl 'http://192.168.1.254/?custom=1&cmd=2019'`, then
   `ffplay -rtsp_transport tcp '<movie-url>'`.
7. Event socket: `nc 192.168.1.254 3333`, then start/stop recording.
8. FTP: `curl -v 'ftp://root:@192.168.1.254/'`.
9. Destructive commands only on a spare card: `3010` format, `4003` delete.
10. Measure: heartbeat latency, 3014 latency, 3015 size/time, download MB/s
    over HTTP vs FTP, RTSP startup time.

## 11. Performance budget suggestions

| Metric | Target |
|---|---|
| App startup to preview | < 2 s after Wi-Fi association |
| Heartbeat | < 200 ms on a healthy link |
| Settings screen open | < 300 ms (cached) |
| Album grid first page | < 1 s (cached list + thumbnails) |
| File download | close to Wi-Fi throughput; test HTTP vs FTP |
| RTSP start | < 2 s |
| Event socket reconnect | < 5 s |

If the camera's HTTP server is the limit, FTP may be the only way to get high
bulk throughput. Measure both.

## 12. Minimal Python client sketch

This is only a starting point; it shows the wire format and the two parser
quirks (3014 is not single-root, 3015 wraps each file in `<ALLFile>`).

```python
import io
import socket
import xml.etree.ElementTree as ET
import requests

BASE = "http://192.168.1.254/"
S = requests.Session()          # keep-alive
S.headers["Connection"] = "keep-alive"

def cmd(cmd_id, par=None, str_value=None, timeout=5):
    params = {"custom": "1", "cmd": str(cmd_id)}
    if par is not None:
        params["par"] = str(par)
    if str_value is not None:
        params["str"] = str_value
    r = S.get(BASE, params=params, timeout=timeout)
    r.raise_for_status()
    return r.text

def current_values():
    text = cmd(3014)
    # 3014 has multiple <Function> roots; wrap it.
    root = ET.fromstring("<root>" + text.split("?>", 1)[-1] + "</root>")
    values = {}
    for fn in root.findall("Function"):
        c = fn.findtext("Cmd")
        v = fn.findtext("Status")
        if c is not None and v is not None:
            values[int(c)] = v
    return values

def file_list():
    text = cmd(3015)
    root = ET.fromstring(text.split("?>", 1)[-1])
    files = []
    for f in root.findall(".//File"):
        path = f.findtext("FPATH") or ""
        url = BASE + path.removeprefix("A:").replace("\\", "/")
        files.append({
            "name": f.findtext("NAME"),
            "path": path,
            "url": url,
            "size": int(f.findtext("SIZE") or 0),
            "timecode": int(f.findtext("TIMECODE") or 0),
        })
    return files

def download(url, dest):
    with S.get(url, stream=True, timeout=(5, 60)) as r:
        r.raise_for_status()
        with open(dest + ".part", "wb") as out:
            for chunk in r.iter_content(1024 * 256):
                out.write(chunk)
    import os
    os.replace(dest + ".part", dest)

def events():
    s = socket.create_connection(("192.168.1.254", 3333), timeout=5)
    buf = b""
    while True:
        chunk = s.recv(4096)
        if not chunk:
            break
        buf += chunk
        while b"</Function>" in buf:
            msg, buf = buf.split(b"</Function>", 1)
            msg += b"</Function>"
            root = ET.fromstring(msg.decode("utf-8", "replace"))
            print(root.findtext("Cmd"), root.findtext("Status"))
```

For production, add retries, structured logging (headers only), a request
queue, and explicit handling for `-256`/`-2`/`-4`.

## 13. What not to do

* Do not send `custom=1` anywhere but first.
* Do not treat `3014` `<Status>` as an error code.
* Do not call `3010`/`3011`/`4004` without confirmation.
* Do not assume `8322`/`8323` are Wi-Fi credentials.
* Do not poll `3014` at high frequency.
* Do not use the event socket without message framing.
* Do not assume HTTP Range works; test it.
* Do not assume the RTSP path; read `2019`.
