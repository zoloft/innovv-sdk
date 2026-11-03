#!/usr/bin/env python3
"""Extract the K5 RTOS command table and menu table.

Usage:
    python3 parse_rtos_tables.py part2.dec.bin out_commands.csv out_menu.csv

The RTOS image is linked at 0x01800000. Addresses below are virtual addresses
from the decompressed image.
"""

import csv
import struct
import sys

BASE = 0x01800000
COMMAND_TABLE = 0x01EE1228
MENU_TABLE = 0x01EE20D0
COMMAND_ENTRY = 20
MENU_ENTRY = 12


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    image_path, commands_out, menu_out = sys.argv[1:]
    data = open(image_path, "rb").read()

    def u32(vaddr: int):
        off = vaddr - BASE
        if off < 0 or off + 4 > len(data):
            return None
        return struct.unpack_from("<I", data, off)[0]

    def cstr(vaddr: int, limit: int = 300) -> str:
        if not vaddr:
            return ""
        off = vaddr - BASE
        if off < 0 or off >= len(data):
            return ""
        end = data.find(b"\0", off, off + limit)
        if end < 0:
            end = off + limit
        return data[off:end].decode("utf-8", "replace")

    # Menu table
    menu = {}
    for i in range(300):
        entry = MENU_TABLE + i * MENU_ENTRY
        menu_id = u32(entry)
        if not menu_id:
            break
        name = cstr(u32(entry + 4))
        options_ptr = u32(entry + 8)
        options = []
        if options_ptr:
            for j in range(64):
                opt = options_ptr + j * 8
                index = u32(opt)
                label_ptr = u32(opt + 4)
                if index is None or index == 0xFFFFFFFF:
                    break
                options.append((index, cstr(label_ptr)))
        menu[menu_id] = (name, options)

    with open(menu_out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["menu_id", "name", "index", "label"])
        for menu_id, (name, options) in menu.items():
            if options:
                for index, label in options:
                    w.writerow([menu_id, name, index, label])
            else:
                w.writerow([menu_id, name, "", ""])

    # Command table
    rows = []
    for i in range(400):
        entry = COMMAND_TABLE + i * COMMAND_ENTRY
        cmd = u32(entry)
        if not cmd:
            break
        field4 = u32(entry + 4)
        handler = u32(entry + 8)
        fieldc = u32(entry + 12)
        menu_id = u32(entry + 16)
        name, options = menu.get(menu_id, ("", []))
        rows.append([
            cmd,
            f"0x{cmd:04X}",
            f"0x{field4:08X}",
            f"0x{handler:08X}" if handler else "",
            f"0x{fieldc:08X}" if fieldc else "",
            menu_id,
            name,
            "|".join(f"{idx}={label}" for idx, label in options),
        ])

    with open(commands_out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["cmd", "cmd_hex", "field_4", "handler", "field_c", "menu_id", "menu_name", "menu_options"])
        w.writerows(rows)

    print(f"wrote {commands_out} ({len(rows)} commands) and {menu_out} ({len(menu)} menu items)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
