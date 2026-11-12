#!/usr/bin/env python3
"""Asegura que un .task de MediaPipe se pueda mapear.

El cargador rechaza el primer archivo del zip si el offset de sus datos no es
múltiplo de 4. Los pose_landmarker oficiales ya lo cumplen (el dato empieza en
el byte 52). Si un paquete no lo cumple, se reescribe sin compresión y con
relleno en el campo extra. También se revisan los zip anidados (metadatos
dentro de un .tflite).
"""

from __future__ import annotations

import argparse
import struct
import sys
import zlib
from pathlib import Path

LOCAL = b"PK\x03\x04"
CENTRAL = b"PK\x01\x02"


def iter_locals(data: bytes):
    pos = data.find(LOCAL)
    found = []
    while pos >= 0 and pos + 30 <= len(data):
        if data[pos : pos + 4] != LOCAL:
            break
        flag, method = struct.unpack_from("<HH", data, pos + 6)
        crc, csize, usize, namelen, extralen = struct.unpack_from("<IIIHH", data, pos + 14)
        name = data[pos + 30 : pos + 30 + namelen]
        data_off = pos + 30 + namelen + extralen
        size = csize
        found.append(
            {
                "header": pos,
                "data": data_off,
                "name": name,
                "method": method,
                "flag": flag,
                "size": size,
                "payload": data[data_off : data_off + size],
            }
        )
        nxt = data_off + size
        if nxt + 4 <= len(data) and data[nxt : nxt + 4] == CENTRAL:
            break
        if nxt + 4 > len(data) or data[nxt : nxt + 4] != LOCAL:
            break
        pos = nxt
    return found


def misaligned(data: bytes) -> list[str]:
    bad = []

    def walk(blob: bytes, origin: int, label: str) -> None:
        for entry in iter_locals(blob):
            absolute = origin + entry["data"]
            if absolute % 4 != 0:
                bad.append(f"{label}{entry['name'].decode('utf-8', 'replace')}@{absolute}")
            payload = entry["payload"]
            if LOCAL in payload:
                walk(payload, origin + entry["data"], label + entry["name"].decode("utf-8", "replace") + "/")

    walk(data, 0, "")
    return bad


def pack(files: list[tuple[bytes, bytes]]) -> bytes:
    parts = bytearray()
    central = bytearray()
    offset = 0
    for name, payload in files:
        namelen = len(name)
        extra_len = (4 - ((offset + 30 + namelen) % 4)) % 4
        extra = b"\x00" * extra_len
        crc = zlib.crc32(payload) & 0xFFFFFFFF
        local = struct.pack(
            "<IHHHHHIIIHH",
            0x04034B50,
            20,
            0,
            0,
            0,
            0,
            crc,
            len(payload),
            len(payload),
            namelen,
            extra_len,
        )
        local += name + extra
        assert (offset + len(local)) % 4 == 0
        parts += local + payload
        central += struct.pack(
            "<IHHHHHHIIIHHHHHII",
            0x02014B50,
            20,
            20,
            0,
            0,
            0,
            0,
            crc,
            len(payload),
            len(payload),
            namelen,
            0,
            0,
            0,
            0,
            0,
            offset,
        )
        central += name
        offset += len(local) + len(payload)
    end = struct.pack(
        "<IHHHHIIH",
        0x06054B50,
        0,
        0,
        len(files),
        len(files),
        len(central),
        offset,
        0,
    )
    return bytes(parts + central + end)


def align_blob(data: bytes) -> bytes:
    if not misaligned(data):
        return data
    entries = iter_locals(data)
    if not entries:
        raise SystemExit("No hay archivos locales que realinear")
    files = []
    for entry in entries:
        payload = entry["payload"]
        if misaligned(payload):
            payload = align_blob(payload)
        files.append((entry["name"], payload))
    rebuilt = pack(files)
    still = misaligned(rebuilt)
    if still:
        raise SystemExit("Sigue desalineado: " + ", ".join(still))
    return rebuilt


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    source = Path(args.input).read_bytes()
    aligned = align_blob(source)
    dest = Path(args.output)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(aligned)
    status = "ya alineado" if aligned == source else "reescrito"
    print(f"{dest.name}: {status}, {len(aligned)} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
