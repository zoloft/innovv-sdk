# Reproduction scripts

These scripts document the extraction steps used for the report. They are
standalone Python 3 scripts with no third-party dependencies (except
`ubi_reader` if you want to extract the rootfs, which is optional).

## 1. Decompress the RTOS partition

The RTOS application is partition 2 at offset `0x0C00`, size `0x3A56E8`:

```bash
python3 bcl1_decompress.py ../../binaries/K5.bin 0x0C00 0x3A56E8 ../../work/part2.dec.bin
```

Expected SHA-256 of the output:

```
88837f23a07467eb5157428be395d1c5affad47c543e85567a5fe075fb8d5395
```

The decompressed image is linked at `0x01800000` and can be opened in Ghidra as
raw ARM little-endian at that address.

## 2. Extract the command and menu tables

```bash
python3 parse_rtos_tables.py ../../work/part2.dec.bin /tmp/commands.csv /tmp/menu.csv
```

This produces:

* `/tmp/commands.csv` — 122 command-table entries with handler addresses,
* `/tmp/menu.csv` — 27 menu items and their option values.

The checked-in copies are `../data/firmware_commands_raw.csv`.

## 3. Extract the K5 preference JSON from the XAPK

```bash
python3 extract_k5_preferences.py \
  ../../binaries/INNOVV_1.0.38.20260618_APKPure.xapk \
  ../data/k5_preferences.json
```

The raw extracted file (with `//` comments) is checked in as
`../data/k5_preferences.raw.json`.

## 4. Extract the UBI rootfs (optional)

The report used `ubi_reader`. The UBI images are at:

* partition 9: `dd if=K5.bin of=ubi_p9.img bs=1 skip=$((0x710c58)) count=$((0x14e0000))`
* partition 10: `dd if=K5.bin of=ubi_p10.img bs=1 skip=$((0x1bf0c98)) count=$((0x1e0000))`

Then:

```bash
ubireader_extract_files -o fw_p9 ubi_p9.img
ubireader_extract_files -o fw_p10 ubi_p10.img
```

Partition 9 contains the rootfs; partition 10 is the empty read/write overlay.

## 5. Android decompilation

```bash
unzip -o INNOVV_1.0.38.20260618_APKPure.xapk -d xapk
jadx -d apk_src --no-res --threads-count 4 xapk/cn.rxt.case.innovv.apk
```

The K5 preference JSON is at
`apk_src/resources/res/raw/ly_innovv_preferences_k5.json` (or directly in the
APK at `res/raw/ly_innovv_preferences_k5.json`).
