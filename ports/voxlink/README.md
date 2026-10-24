# VoxLink networking subset for Unbox

Upstream: https://github.com/AUGUHDAR/VoxLink
Pinned commit: b80595db5485f455db31c5454d897a960e664393 (2026-10-20).
License: LGPL-3.0; see LICENSE. Original file hashes are in upstream.json.

Unbox reuses the UDP/TCP punchers, reliable UDP stream, STUN and UPnP implementations.
It supplies its own invitation-scoped signaling, authenticated connection selection,
socket lifecycle and Minecraft bridge. It does not load VoxLink's entrypoints, UI,
public lobby, account system, mod synchronization or external binary downloader.

Local modifications:
- StunProbe uses an SLF4J logger directly and omits persistent STUN-cache writes.
- ReliableUdpTransport omits upstream LogUploadManager callbacks. No logs are uploaded.
- Unbox caps DATA/FEC frames at 1200 UDP bytes including authentication, and uses
  periodic keepalives without echoing received keepalives.
- UdpHolePuncher omits P2PBridge's global timeout registry. Unbox owns every puncher
  and calls cancel() when it loses, times out, or its invitation/world is closed.

The build bundles this notice, license and corresponding modified sources. Edit
these sources and rebuild with scripts/prepare-voxlink.py and the normal module
build scripts to replace the networking subset.
