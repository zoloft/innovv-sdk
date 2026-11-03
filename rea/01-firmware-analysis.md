# K5 firmware analysis

Artifact: `binaries/K5.bin`
Size: 31,263,896 bytes
SHA-256: `39b057e19ca7e3252f29ef257f0f496d471b90c54450aa3f794cbc6d002cc88e`

The image is a **Novatek NT96685** firmware. The header at offset 0 contains the
model string `NT96685`, the `MODELEXT` tag, the build date `20210827`, and the
`IK5` model identifier. The Linux-side profile identifies the product as
`MODEL=IK5`, `CHIP=96685`, `UI_STYLE=UI_STYLE_SPORTCAM`.

## 1. Container layout

The partition table is at file offset `0x80`. Each entry is 12 bytes:
`offset (LE32), size (LE32), partition_id (LE32)`.

| id | offset | size | end | contents |
|---:|---:|---:|---:|---|
| 1 | `0x000000C8` | `0x00000B38` | `0x00000C00` | Config / MODELEXT header |
| 2 | `0x00000C00` | `0x003A56E8` | `0x003A62E8` | **RTOS (uITRON) application**, BCL1/LZ77 compressed |
| 3 | `0x003A62E8` | `0x000718E8` | `0x00417BD0` | Bootloader / u-boot stage |
| 5 | `0x00417BD0` | `0x002F9046` | `0x00710C16` | **Linux kernel** uImage, `Linux-4.1.0` |
| 9 | `0x00710C18` | `0x014E0040` | `0x01BF0C58` | **UBI volume `rootfs`** (Linux rootfs, UBIFS) |
| 10 | `0x01BF0C58` | `0x001E0040` | `0x01DD0C98` | **UBI volume `rootfs`** (read/write overlay) |

There are no gaps except a 2-byte alignment gap before partition 9.

### Partition 1 — config

Contains the model/build strings, `MODELEXT` records, and board configuration.
Not needed for the API.

### Partition 2 — RTOS application (the interesting one)

* Magic `BCL1`, CRC `0xDCA1`, compression algorithm `0x0009` (BCL LZ77).
* Uncompressed size `0x76406C` = 7,749,740 bytes.
* Compressed size `0x3A56D8` = 3,823,320 bytes.
* Decompressed image is a raw ARM little-endian image. The exception vector
  table at offset 0 loads `pc` from entries that point to `0x01800400`,
  `0x01800800`, etc., so the image is linked at **`0x01800000`**.
* The decompressed image contains:
  * `INNOVV IK5` model strings and `IK5.5G.20210827.V1.1.12`.
  * `ImageUnit_NetHTTP#1.00.000_SHA.50C1A6EA` — the HTTP server.
  * `ImageUnit_NetRTSP#1.00.001` / `RtspNvt#1.01.010` — RTSP/streaming.
  * `ImageUnit_StreamSender#1.00.001`, `ImageUnit_VdoIn`, `ImageUnit_AudEnc`,
    `ImageApp_Photo`, `MovieInterface`, `Playback`, `WifiCmd`, `HfsNvt`.
  * The complete `custom=1&cmd=` command dispatcher and command table.

The RTOS image is the authoritative source for the HTTP API. The Linux rootfs
does **not** implement the `custom=1&cmd=` API; the Linux binaries are mostly
BusyBox, Wi-Fi tools, the RTSP daemon (`nvtrtspd` → `libnvtlive555.so`), and
kernel modules.

### Partition 3 — bootloader

Starts with an ARM exception-vector table (`18 f0 9f e5` repeated). Strings
include `bootcmd=nvt_boot`. This is the u-boot stage that boots the kernel.

### Partition 5 — Linux kernel

uImage header at offset 0 (`0x27051956`):

* name `Linux-4.1.0`
* OS `Linux`, architecture `ARM`, type `kernel`, compression `none`
* load address `0x80002000`, entry `0x80002000`
* data size `0x2F9006`

The kernel contains the NT96685 board support, MMC/NAND, USB, Wi-Fi, ISP, and
the nvt streaming drivers.

### Partitions 9 and 10 — UBI/UBIFS rootfs

Both partitions have a 0x40-byte `CKSM` header followed by a UBI image.

* PEB size 128 KiB (`0x20000`), LEB size 126,976 bytes (`0x1F000`).
* Partition 9: UBI volume name `rootfs`, 167 PEBs, 165 data PEBs.
* Partition 10: UBI volume name `rootfs`, 15 PEBs, 13 data PEBs.

Partition 9 contains a complete UBIFS rootfs (~36 MiB extracted). Partition 10
contains no inodes; it is inferred to be the initially-empty **read/write
overlay** volume (`rootfs1`) that `S20_FS_Overlay` attaches and mounts at
`/mnt/overlay_rw0`.

The rootfs is a Buildroot 2016.08.1 / glibc 2.23 / ARM hard-float system. Key
contents:

| Path | Notes |
|---|---|
| `/bin/busybox` | BusyBox shell and applets |
| `/bin/nvtrtspd` | RTSP daemon front-end (8 KiB, links `libnvtlive555.so`) |
| `/lib/libnvtlive555.so` | Live555-based RTSP server |
| `/usr/local/bin/hostapd` | Realtek hostapd for RTK8821CS |
| `/bin/wpa_supplicant` | Wi-Fi station mode |
| `/etc/init.d/` | Startup scripts (`S10_Net`, `S15_NvtAppInit`, `S20_FS_Overlay`, `S90_yq_init`, …) |
| `/usr/share/wifiscripts/` | Realtek/AP mode scripts |
| `/lib/modules/4.1.0/` | Wi-Fi, ISP, USB, NAND, FS modules |

## 2. BCL1 decompression

The RTOS partition uses Novatek's modified **Basic Compression Library LZ77**
(`bfc4ntk`). The format is:

```
0x00  "BCL1"
0x04  CRC16 (LE)
0x06  algorithm (BE16): 0x09 = LZ77, 0x0B = LZMA, 0x0C = zlib
0x08  uncompressed size (BE32)
0x0C  compressed size (BE32)
0x10  compressed stream
```

For LZ77, the first byte of the stream is the **marker** (`0xAD` for this
image). Then:

* literal byte → output it
* `marker, 0x00` → literal marker byte
* `marker, varint(length), varint(offset)` → copy `length` bytes from
  `output[-offset]`

The decompressed image is `work/part2.dec.bin` (SHA-256
`88837f23a07467eb5157428be395d1c5affad47c543e85567a5fe075fb8d5395`).

## 3. RTOS HTTP server and command table

The HTTP server module is `ImageUnit_NetHTTP#1.00.000_SHA.50C1A6EA`. Strings
include `NetHTTP_Open`, `ISF_NetHTTP_SetPortParam`, and
`ISF_NetHTTP_SetPortParam`/`NetHTTP`. The default control API is plain HTTP on
port 80.

### CGI dispatcher

The request handler is at `0x01B9826C` (Ghidra `FUN_01b9826c`). Its behavior:

1. Logs the request (`Data2 path`, `data`, `argument`, `segmentCount`).
2. Prefix-compares the query string against the literal `custom=1&cmd=`
   (address `0x01E87658`). **`custom=1` must be first**; otherwise the request is
   treated as a non-API page request.
3. If not found, serves `A:\test.htm` (a simple test page).
4. Parses up to 16 space-separated integers after `cmd=` using the format
   `%d %d %d …` (address `0x01E64ADC + 0x30`).
5. If the query contains `&par=`, parses the second integer group; if it
   contains `&str=`, passes the string to the handler.
6. Looks up the command in a **122-entry table** at `0x01EE1228`; each entry is
   20 bytes: `cmd_id, field_4, handler, field_C, menu_id`.
7. Calls the handler with `(cmd, request, output_buffer, output_size_ptr,
   header_buffer, header_size)`.
8. If no handler is registered, calls the default callback
   `_DAT_02102870` or returns an error.

The command table is at file offset `0x6E1228` in `work/part2.dec.bin`.
A raw dump is in [data/firmware_commands_raw.csv](data/firmware_commands_raw.csv).

### Menu table

Many simple settings are not implemented as dedicated handlers. They use the
generic parameter path and a **menu table** at `0x01EE20D0`:

```
struct MenuItem {          // 12 bytes
    u32 menu_id;
    const char *name;
    const MenuOption *options;
};
struct MenuOption {        // 8 bytes
    u32 index;
    const char *label;
};
```

The menu table has 27 items. It is used by:

* `cmd=3031` — return the setting menu as XML.
* `cmd=3014` — return the current value of each menu-backed setting.
* `cmd=3030` — return supported resolutions.

The menu names/options extracted from the firmware are in
`data/firmware_commands_raw.csv` (columns `menu_name`, `menu_options`).

### K5 menu contents (firmware side)

The firmware menu table names include:

`Resolution`, `Bitrate`, `Loop Recording`, `Movie Format`, `Movie Encode`,
`Front Mirror`, `Rear Mirror`, `Movie Audio`, `Time-lapse Recording`, `GPS`,
`GPS Info Stamp`, `Speed Unit`, `G-sensor Sensitivity`, `Parking Mode`, `EIS`,
`Exposure`, `Contrast`, `Saturation`, `Sharpness`, `WB`, `Frequency`,
`Live Video Source`, `WifiOff Time`, `Wi-Fi`, `PHOTO_SIZE`, `gps update freq`,
`Gsensor Info Stamp`.

The app's K5-specific menu (which is what the user actually saw) is smaller and
is extracted in [data/k5_preferences.json](data/k5_preferences.json); see
[02-android-app-analysis.md](02-android-app-analysis.md).

### Response formats

The firmware builds XML strings from templates at `0x01CC80xx`:

| Purpose | Format |
|---|---|
| XML header | `<?xml version="1.0" encoding="UTF-8" ?>\n` |
| Function open/close | `<Function>\n` / `</Function>\n` |
| Command/status | `<Cmd>%d</Cmd>\n<Status>%d</Status>\n` |
| Value | `<Value>%lld</Value>` |
| Two values | `<Value>%lld</Value>\n<Value1>%lld</Value1>\n` |
| String | `<String>%s</String>` |
| File list item | `<ALLFile><File>\n<NAME>%s</NAME>\n<FPATH>%s</FPATH>\n<SIZE>%lld</SIZE>\n<TIMECODE>%ld</TIMECODE>\n<TIME>%04d/%02d/%02d %02d:%02d:%02d</TIME>\n<ATTR>%d</ATTR></File>\n</ALLFile>\n` |
| Menu item | `<Item>\n<Cmd>%d</Cmd>\n<Name>%s</Name>\n<MenuList>\n<Option>\n<Index>%d</Index>\n<Id>%s</Id>\n</Option>\n</MenuList>\n</Item>\n` |
| Live view | `<MovieLiveViewLink>…</MovieLiveViewLink>\n<PhotoLiveViewLink>…</PhotoLiveViewLink>\n` |
| Wi-Fi info | `<SSID><![CDATA[%s]]></SSID>\n<PASSPHRASE><![CDATA[%s]]></PASSPHRASE>\n` |
| Wi-Fi scan | `<AP_index>\n<SSID>%s</SSID>\n<Auth_type>%d</Auth_type>\n<RSSI>%d dBm</RSSI>\n</AP_index>\n` |
| GPS info | `<timestamp>%ld</timestamp><lng>%f</lng>\n<lat>%f</lat>\n<alt>%f</alt>\n<spd>%f</spd>\n` + `<SAT>\n<SatNum>%d</SatNum>\n<SNR>%d</SNR>\n</SAT>\n` |
| Movie file info | `Width:%d, Height:%d, Length:%d sec` |

The 3014 handler is special: it emits `<Cmd>N</Cmd>\n<Status>V</Status>` for
each menu-backed setting, where **`V` is the current value**, not a status code.
This is confirmed by the handler code at `0x01A8D89C`: it calls
`FUN_01a8637c(menu_id)` and passes the return value as the second `%d`.

### Bounded response buffer

The file-list handler at `0x01A8C244` and the query handler at `0x01A8D89C`
reserve 0x200 bytes at the end of the output buffer and stop when full. The stock
app uses a large client buffer, but the server-side output buffer is bounded.
For large SD cards, a `cmd=3015` response may be truncated; see
[04-media-and-transfers.md](04-media-and-transfers.md) for mitigation.

## 4. Linux side and services

### Boot and network

`/etc/init.d/S10_Net`:

* loads the Ethernet/Wi-Fi modules,
* starts `inetd`,
* starts `uctrl usys -notify net_init_ok`.

`/etc/init.d/S15_NvtAppInit` starts `usbipc` and the NVT app init.

`/etc/init.d/S20_FS_Overlay` attaches the `rootfs1` UBI volume and mounts it as
the read/write overlay for `/etc`, `/var`, and `/lib/modules`.

`/etc/init.d/S90_yq_init` starts the INNOVV-specific daemons:

* `/etc/yq_daemon` — restarts `nvtrtspd` whenever `wlan0` exists.
* `/etc/auto_check_wifi.sh` — detects the Wi-Fi module (RTL8821CS or Broadcom
  AP6xxx) and copies the matching scripts.
* `/etc/check_wifi_connect.sh` — monitors hostapd events and notifies the RTOS
  via `uctrl`.

### Wi-Fi AP

`/etc/udhcpdw.conf`:

* interface `wlan0`
* DHCP range `192.168.1.20`–`192.168.1.253`
* router / camera address `192.168.1.254`
* lease 10 days

The sample hostapd config `/etc/wifiap_wpa2.conf` has SSID `680apwpa2` and
passphrase `12345678`, but the actual values are generated at runtime by the
Wi-Fi scripts/RTOS. Read the current values with `cmd=3029` or change them with
`cmd=3003`/`3004`. The K5 supports 2.4 GHz and 5 GHz (`cmd=8201`; strings
`IK5.5G`, `SetWifiHz`, `NVT_WIFI_5G`, `NVT_WIFI_24G`).

### FTP and TFTP

`/etc/inetd.conf` enables:

```
21 stream tcp nowait root ftpd  ftpd  -w /mnt/sd
69 dgram  udp wait   root tftpd tftpd -l -c /mnt/sd
```

* FTP server root is `/mnt/sd` (the SD card).
* `/etc/passwd` is `root::0:0:root:/root:/bin/sh` — **root has an empty
  password** in the rootfs. BusyBox `ftpd` may therefore allow `root` with an
  empty password; this was not tested against the device.
* TFTP has no authentication and `-c` allows file creation.
* These services are a potentially much faster path for bulk media transfer
  than the camera's HTTP server. See
  [04-media-and-transfers.md](04-media-and-transfers.md).

### RTSP / live view

`nvtrtspd` is a small front-end that calls `nvtlive555_open` and the NVT
stream-sender API. `libnvtlive555.so` is a Live555-based RTSP server with
H.264/H.265/MJPEG framers. Strings include:

* `rtsp://%s:%u/%s`
* `.mp4`, `.mov`
* `set rtsp port=%d http_port=%d https_port=%d`

The RTOS `cmd=2019` handler builds the actual live-view URLs. The stock app
defaults to `rtsp://192.168.1.254:554/xxx.mp4` for movie mode and
`http://192.168.1.254:8192` for photo mode. The firmware's own format strings
use `rtsp://%s/xxx.mov` and `http://%s:8192`. Always prefer the URLs returned by
`cmd=2019`.

### Ports observed in the firmware

| Port | Service | Evidence |
|---:|---|---|
| 80/tcp | RTOS HTTP control API | `ImageUnit_NetHTTP`, dispatcher |
| 554/tcp | RTSP (default) | `rtsp://%s:%u/%s`, `rtsp port=%d` |
| 8192/tcp | MJPEG/JPEG live view | `<...>http://%s:8192</...>` |
| 3333/tcp | Camera event push | app `core.d(ip, 3333)`; `ly/l.java` |
| 21/tcp | FTP | `inetd.conf` |
| 69/udp | TFTP | `inetd.conf` |
| 123/udp | NTP client applet present (not a server) | `etc/services`, BusyBox `ntpd` applet |

## 5. Firmware version and model strings

* RTOS: `IK5.5G.20210827.V1.1.12`
* Model: `INNOVV IK5`, `INNOVV`, `5G_INNOVV_K5_`, `INNOVV_K5_`
* Chip: `NT96685`
* Linux kernel: `Linux-4.1.0`
* Rootfs: Buildroot 2016.08.1, glibc 2.23, ARM hard-float
* Wi-Fi: Realtek RTL8821CS (`8821cs.ko`), Broadcom AP6xxx fallback
* Sensors: Sony IMX415 (front), IMX291 (rear) per `profile_prjcfg`

## 6. Security observations

* The HTTP API is unauthenticated. The Wi-Fi AP is the only access control.
* `cmd=3029` returns the Wi-Fi SSID and passphrase in plaintext.
* FTP runs as root with an empty root password in `/etc/passwd`; TFTP is
  unauthenticated and writable.
* The control API is plain HTTP; no TLS is used by the stock app. The firmware
  has `https_port` in a debug/port-setting string, but no HTTPS listener was
  confirmed.
* `cmd=3010` formats the SD card and `cmd=4003`/`4004` delete files without
  authentication.

## 7. Unknowns

* Exact server-side response-buffer size and truncation behavior for large
  `cmd=3015` responses.
* Whether the RTOS HTTP server accepts concurrent requests or serializes them.
* Exact RTSP path/port on the K5 hardware; use `cmd=2019`.
* FTP/TFTP login behavior on the real device (root/empty password is inferred
  from `/etc/passwd`).
* Firmware-update signature verification and the exact precondition for
  `cmd=3013`.
* `ATTR` bit meanings in the file list.
* The precise mapping of some `3020` event status codes.
