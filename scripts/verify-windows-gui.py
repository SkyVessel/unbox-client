#!/usr/bin/env python3
"""Reject a release launcher that would create a Windows console at startup."""
import pathlib, struct, sys

path = pathlib.Path(sys.argv[1])
data = path.read_bytes()
assert data[:2] == b'MZ', 'Missing executable header'
pe = struct.unpack_from('<I', data, 0x3c)[0]
assert data[pe:pe+4] == b'PE\0\0', 'Missing PE header'
optional = pe + 24
assert struct.unpack_from('<H', data, optional)[0] in (0x10b, 0x20b), 'Unknown PE format'
subsystem = struct.unpack_from('<H', data, optional + 68)[0]
assert subsystem == 2, f'Expected Windows GUI subsystem (2), got {subsystem}'
print(f'{path.name}: Windows GUI subsystem confirmed (no launcher console)')
