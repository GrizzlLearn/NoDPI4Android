# HevSocks5Tunnel 2.19.3

Official release: https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.19.3

Artifact: `hev-socks5-tunnel.aar` (renamed to include the pinned version).
SHA-256: `ab6c581e6157680cfe5687ee7bbdcf95405c006c2db3701879ec44b9e5f83836`.
MIT license: `hev-LICENSE`. Four Android ABIs are bundled by upstream.

Source and build instructions: https://github.com/heiher/hev-socks5-tunnel/tree/2.19.3
JNI binding included in the AAR: `hev.htproxy.TProxyService`.

## NoDPI routing build

`hev-socks5-tunnel-2.19.3-nodpi.aar` is the application's patched build with
original-flow UID routing to different loopback SOCKS ports. It is built from
the vendored upstream release source plus the complete local patch. See
`native/README.md` and `scripts/build-native-vpn.sh` for reproduction, and
`scripts/test-native-routing.py` for the concurrent routing worker check.
The original AAR above remains an unmodified Java-binding/manifest packaging input.
