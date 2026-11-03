#!/usr/bin/env python3
"""Decompress a Novatek BCL1/LZ77 partition.

Usage:
    python3 bcl1_decompress.py K5.bin 0x0C00 0x3A56E8 part2.dec.bin

The BCL1 header is:
    0x00  "BCL1"
    0x04  CRC16 (LE)
    0x06  algorithm (BE16): 0x09 LZ77, 0x0B LZMA, 0x0C zlib
    0x08  uncompressed size (BE32)
    0x0C  compressed size (BE32)
    0x10  compressed stream
"""

import struct
import sys


def decompress_lz77(stream: bytes, out_size: int) -> bytes:
    marker = stream[0]
    i = 1
    out = bytearray()

    def varint() -> int:
        nonlocal i
        value = 0
        while True:
            b = stream[i]
            i += 1
            value = (value << 7) | (b & 0x7F)
            if not (b & 0x80):
                return value

    while i < len(stream) and len(out) < out_size:
        symbol = stream[i]
        i += 1
        if symbol == marker:
            b = stream[i]
            if b == 0:
                i += 1
                out.append(marker)
            else:
                length = varint()
                offset = varint()
                for _ in range(length):
                    out.append(out[-offset])
        else:
            out.append(symbol)
    return bytes(out)


def main() -> int:
    if len(sys.argv) != 5:
        print(__doc__)
        return 2
    path = sys.argv[1]
    offset = int(sys.argv[2], 0)
    size = int(sys.argv[3], 0)
    output = sys.argv[4]

    with open(path, "rb") as f:
        f.seek(offset)
        header = f.read(16)
        if header[:4] != b"BCL1":
            raise SystemExit("not a BCL1 partition")
        algo = struct.unpack(">H", header[6:8])[0]
        out_size = struct.unpack(">I", header[8:12])[0]
        packed_size = struct.unpack(">I", header[12:16])[0]
        if algo != 0x09:
            raise SystemExit(f"unsupported algorithm 0x{algo:02X} (this script handles LZ77)")
        stream = f.read(packed_size)

    data = decompress_lz77(stream, out_size)
    with open(output, "wb") as f:
        f.write(data)
    print(f"wrote {output} ({len(data)} bytes, expected {out_size})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
