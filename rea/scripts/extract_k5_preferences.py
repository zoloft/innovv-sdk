#!/usr/bin/env python3
"""Extract the K5 preference JSON from the INNOVV XAPK.

Usage:
    python3 extract_k5_preferences.py INNOVV.xapk out_k5_preferences.json

The XAPK contains the base APK and split APKs. The K5 definition is at
res/raw/ly_innovv_preferences_k5.json inside the base APK. The file contains
`//` comments, which are stripped before writing valid JSON.
"""

import io
import json
import re
import sys
import zipfile


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    xapk, out = sys.argv[1:]
    with zipfile.ZipFile(xapk) as z:
        apk_name = next(n for n in z.namelist() if n.endswith(".apk") and n == "cn.rxt.case.innovv.apk")
        apk_bytes = z.read(apk_name)
    with zipfile.ZipFile(io.BytesIO(apk_bytes)) as apk:
        raw = apk.read("res/raw/ly_innovv_preferences_k5.json").decode("utf-8")
    # Strip full-line // comments only; the JSON has no inline // in strings.
    cleaned = re.sub(r"^\s*//.*$", "", raw, flags=re.MULTILINE)
    data = json.loads(cleaned)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"wrote {out}: {len(data.get('preferences', []))} entries")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
