# Data files

| File | Description |
|---|---|
| `api_commands.csv` | Curated K5/Novatek command reference (127 rows). Includes commands used by the stock K5 app, firmware-only commands, and the TCP-3333 event commands. |
| `firmware_commands_raw.csv` | Raw command table extracted from the decompressed RTOS image at `0x01EE1228`. Columns: `cmd`, `cmd_hex`, `field_4`, `handler`, `field_c`, `menu_id`, `menu_name`, `menu_options`. |
| `k5_settings.csv` | K5 settings screen definition from the app's bundled JSON. Columns: `order`, `ui`, `cmd`, `name_trn`, `note`, `values`, `enable`. |
| `k5_preferences.json` | Cleaned valid JSON of the K5 preference definition. |
| `k5_preferences.raw.json` | Exact extracted file, including `//` comments. |
| `firmware_partitions.txt` | K5.bin partition table with offsets, sizes, and descriptions. |
| `sha256.txt` | SHA-256 of the analyzed artifacts. |
| `sizes.txt` | Sizes of the analyzed artifacts. |

## `api_commands.csv` columns

* `cmd` — decimal command ID.
* `name` — human-readable purpose.
* `params` — request parameters (`par`/`str`/event).
* `response` — response shape or binary type.
* `evidence` — firmware handler address, app method, or data source.
* `notes` — values, caveats, confidence.

## `values` encoding in `k5_settings.csv`

`values` is a `|`-separated list of `index=label` pairs. The index is what the
app sends as `&par=<index>`. If the JSON item had no explicit `key`, the array
position is used (this matches the app's `n(JSONObject)` implementation).
