# NoDPI per-application native routing

Upstream source: HevSocks5Tunnel 2.19.3, MIT and bundled dependency licenses.
Release: https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.19.3
Vendored source archive SHA-256:
`8aaa33b344cab03a5cfc3675b9aaae55c6ee6088d78678ad1fbb44855b5ba570`.

The archive is the official complete release source, including its submodule
sources and licenses. `patches/per-app-routing.patch` is the complete local diff.
The unmodified upstream AAR remains the input for Java binding/manifest packaging.

Build with Android SDK, NDK 28.2.13676358, Python 3, patch, tar, and a JDK:

```sh
ANDROID_HOME=/tmp/nodpi-android-sdk scripts/build-native-vpn.sh
```

Output: `app/libs/hev-socks5-tunnel-2.19.3-nodpi.aar`, four ABIs, Android 29+,
16 KiB ELF page alignment. Source and intermediate objects are retained in
`native/.build/`, which is ignored by Git. The SDK and NDK are not deleted.

## JNI routing contract

Before opening a TCP or UDP flow's SOCKS connection, native code invokes:

```kotlin
@JvmStatic
fun selectPort(
    protocol: Int,
    localAddress: String,
    localPort: Int,
    remoteAddress: String,
    remotePort: Int,
): Int
```

The binding class is `com.gvcoder.nodpi.core.NativeRouting`. Protocol is 6 for TCP,
17 for UDP. Addresses and ports describe the original application's connection,
before any SOCKS connection is opened. In the transparent lwIP stack, its
`pcb.remote` fields are the original application source, and its `pcb.local`
fields are the original destination.

Return 0 to retain the configured loopback SOCKS endpoint; 1..65535 selects an
alternate port on the same loopback address; negative values reject the flow.
The result is stored in session state and never re-evaluated for subsequent
packets. A Java exception or invalid return value also rejects the flow.

The Java callback resolves the original socket's UID using Android's active-VPN
connection-owner API. Never infer an application from destination domains/IPs.
Unknown UIDs must not silently downgrade an externally routed flow to direct
access. External proxy credentials remain in the Java connector and are not
passed through JNI or written to this native YAML configuration.

The callback is invoked on one dedicated native routing worker pthread with a
normal pthread stack, attached to the JVM as a daemon. The engine waits for the
result. This avoids calling JVM code from Hev coroutine stacks. The callback
must not wait for work scheduled back onto the engine thread or perform network
requests. The worker is reused across flows and service restarts. A consumer ProGuard
rule retains the callback class/method in optimized builds.

## Local checks

After building, run `python3 scripts/test-native-routing.py`. It compiles the
exact route-worker implementation with a deterministic stand-in for the JVM
callback, then checks concurrent flows to one destination selecting different
ports, IPv6 tuples, rejected flows and standalone fallback. This tests native
request isolation; it does not replace device testing of Android's UID lookup
or the complete VPN stack.
