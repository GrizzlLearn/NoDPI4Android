<div align="center">
  <p>
    <a href="#"><img src="./assets/ico.png" height="150px" alt="logo" /></a>
  </p>
</div>

# NoDPI for Android
<b><i>Say NO to blocking!</b></i>

[![Visitors](https://visitor-badge.laobi.icu/badge?page_id=GVCoder09.NoDPI4Android)](https://github.com/GVCoder09/NoDPI4Android)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL3.0-yellow.svg)](https://www.gnu.org/licenses/gpl-3.0.txt)
[![GitHub Release](https://img.shields.io/github/v/release/GVCoder09/NoDPI4Android)](https://github.com/GVCoder09/NoDPI4Android)
[![Github All Releases](https://img.shields.io/github/downloads/GVCoder09/NoDPI4Android/total.svg)](https://github.com/GVCoder09/NoDPI4Android)
[![Github Latest Release](https://img.shields.io/github/downloads/GVCoder09/NoDPI4Android/latest/total.svg)](https://github.com/GVCoder09/NoDPI4Android)
[![GitHub Stars](https://img.shields.io/github/stars/GVCoder09/NoDPI4Android.svg?style=dark)](https://github.com/GVCoder09/NoDPI4Android)
[![GitHub Forks](https://img.shields.io/github/forks/GVCoder09/NoDPI4Android.svg?style=dark)](https://github.com/GVCoder09/NoDPI4Android)
[![GitHub Last commit](https://img.shields.io/github/last-commit/GVCoder09/NoDPI4Android.svg?style=dark)](https://github.com/GVCoder09/NoDPI4Android)

> [!IMPORTANT]
> This project is a fork of the repository https://github.com/theo0x0/nodpi and is developed independently. Do not confuse with https://github.com/raspabamos/nodpi !

> [!NOTE]
> This version is only for Android. **The Desktop version is available here:** https://github.com/GVCoder09/NoDPI

🌐 **[Website & Documentation](https://gvcoder09.github.io/nodpi_site/)** - user-friendly guide and overview

**Read this in other languages / Читайте на других языках**
- **[English](README.md)**
- [Русский](README.ru.md)

## Table of Content

- [NoDPI for Android](#nodpi-for-android)
  - [Table of Content](#table-of-content)
  - [Description](#description)
    - [How does this work](#how-does-this-work)
    - [Sites currently unavailable via NoDPI](#sites-currently-unavailable-via-nodpi)
    - [Alternatives](#alternatives)
      - [Android](#android)
      - [Other platforms](#other-platforms)
  - [System requirements](#system-requirements)
  - [Terms of Use and Disclaimer](#terms-of-use-and-disclaimer)
  - [Quick start](#quick-start)
    - [Local VPN](#local-vpn-android-10)
    - [Manual HTTP proxy](#manual-http-proxy)
    - [Additional Blacklists](#additional-blacklists)
  - [VPN routes, DNS and diagnostics](#vpn-routes-dns-and-diagnostics)
    - [External SOCKS5](#external-socks5)
    - [DNS and IPv4-only mode](#dns-and-ipv4-only-mode)
    - [Resource management and network changes](#resource-management-and-network-changes)
    - [Journal and connection checks](#journal-and-connection-checks)
    - [Settings transfer](#settings-transfer)
  - [Building APK from source code](#building-apk-from-source-code)
  - [FAQ](#faq)
  - [Star History](#star-history)

## Description
  NoDPI4Android is a graphical add-on [NoDPI](https://github.com/GVCoder09/NoDPI). This application is designed to run on Android devices only, Windows and Linux version available [here](https://github.com/GVCoder09/NoDPI/releases)

  NoDPI is a utility designed to bypass DPI (Deep Packet Inspection). What is DPI? DPI is a technology for inspecting network packets based on their content to regulate and filter traffic. It is often used by internet service providers to censor and block access to certain web resources and protocols. The goal of this program is to "trick" DPI by modifying network packets, although in reality, this trickery is often simply a consequence of a lack of computing resources and time. 
 
  This utility was developed primarily for Russian users as a simpler (but less powerful) replacement for similar tools. In most cases, it gets the job done; in particular, it allows you to use YouTube without any problems.

  Access depends on the provider and the type of filtering. This fork adds a local VPN
  with app selection, optional external SOCKS5 routes, DNS over HTTPS and IPv4-only mode.
  Instagram and YouTube were tested on the user's network; availability on other networks
  is not guaranteed. Local TLS fragmentation cannot overcome IP-based blocking.

<div align="center">
  <p>
    <img src="./assets/screenshot.png" height="600px"/>
  </p>
</div>

### How does this work

NoDPI can capture selected apps through a local VPN or accept traffic through a manually configured HTTP proxy. Locally routed HTTPS traffic passes through the proxy and TLS fragmenter. The program intercepts ClientHello of outing connections and fragments them according to the following method:

The packet contains a field that holds the SNI record. The ClientHello message is split into several TLS records: the portion preceding the SNI, the portion containing the SNI (character by character), and the portion following the SNI. A header indicating the ClientHello type is added to each of these parts. Unless out-of-order packet handling (disordering) is disabled, the first of these parts is sent with a TTL=1, causing it to be dropped by the nearest network device. As a result, the DPI (Deep Packet Inspection) system fails to identify the domain for which the connection is being established and allows the connection to proceed. The operating system subsequently retransmits the first part with a standard TTL.

Also, the TLS version is replaced by version 1.3, which is the most modern (but this does not mean that your data begins to be transmitted according to the specifications of this version). All this together allows you to get around the lock.

> [!NOTE]
> Sites hosted on Cloudflare are sent without fragmentation. DPI treats connections to Cloudflare differently: a blocked domain is dropped even when fragmented, and a connection whose domain DPI could not read is frozen after the first ~16 KB. Fragmentation cannot unblock such sites and only breaks the allowed ones.

> [!IMPORTANT]
> Local DPI bypass processes HTTPS. Plain HTTP is forwarded for compatibility. The optional external SOCKS5 route supports TCP and does not rely on TLS fragmentation.

No root access is required. Other apps’ HTTPS remains encrypted; NoDPI does not decrypt it. Connection metadata is stored in a private local journal. Optional DoH sends DNS queries to Cloudflare, and external SOCKS5 sends selected traffic through the server you configure.

The app is written in Kotlin with Jetpack Compose (Material 3 Expressive). The proxy runs in a foreground service and can be toggled from a quick settings tile.

### Sites currently unavailable via NoDPI

Local TLS fragmentation is not a general-purpose tunnel. Telegram/MTProto,
WhatsApp, Discord calls and IP-blocked destinations may require a reachable external
route. An external SOCKS5 server can be configured for selected apps, but its IP must
be reachable from the user's network. External UDP is currently blocked, so calls and
other UDP-only features are not supported through that route. Existing Cloudflare
fragmentation exclusions remain in effect.

### Alternatives

#### Android

- **[DPI Tunnel for Android](https://github.com/nomoresat/DPITunnel-android)** by @zhenyolka (for Android)
- **[PowerTunnel for Android](https://github.com/krlvm/PowerTunnel-Android)** by @krlvm (for Android)
- **[ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid/)** for Android

#### Other platforms

- **[NoDPI](https://github.com/GVCoder09/NoDPI)** by @GVCoder09 (for Windows and Linux)
- **[GoodbyeDPI](https://github.com/ValdikSS/GoodbyeDPI)** by @ValdikSS (for Windows)
- **[zapret](https://github.com/bol-van/zapret)** by @bol-van (for MacOS, Linux and Windows)
- **[Green Tunnel](https://github.com/SadeghHayeri/GreenTunnel)** by @SadeghHayeri (for MacOS, Linux and Windows)
- **[DPI Tunnel CLI](https://github.com/nomoresat/DPITunnel-cli)** by @zhenyolka (for Linux and routers)
- **[PowerTunnel](https://github.com/krlvm/PowerTunnel)** by @krlvm (for Windows, MacOS and Linux)
- **[SpoofDPI](https://github.com/xvzc/SpoofDPI)** by @xvzc (for macOS and Linux)
- **[SpoofDPI-Platform](https://github.com/r3pr3ss10n/SpoofDPI-Platform)** by @r3pr3ss10n (for Android, macOS, Windows)
- **[GhosTCP](https://github.com/macronut/ghostcp)** by @macronut (for Windows)
- **[ByeDPI](https://github.com/hufrea/byedpi)** for Linux/Windows
- **[youtubeUnblock](https://github.com/Waujito/youtubeUnblock/)** by @Waujito (for OpenWRT/Entware routers and Linux)

## System requirements

Manual HTTP proxy mode requires Android 7.0 or newer. Local VPN and per-app routing require Android 10 or newer.
The application also uses permission to send notifications. For the correct operation of the proxy service, it is recommended to disable energy consumption optimization in the application's system settings

## Terms of Use and Disclaimer

> [!WARNING]
> By using this software in any form or distributing it, you accept the following terms of use and disclaimer

The developer and/or supplier of this software shall not be liable for any loss or damage, including but not limited to direct, indirect, incidental, punitive or consequential damages arising out of the use of or inability to use this software, even if the developer or supplier has been advised of the possibility of such damages.

The developer and/or supplier of this software shall not be liable for any legal consequences arising out of the use of this software. This includes, but is not limited to, violation of laws, rules or regulations, as well as any claims or suits arising out of the use of this software. The user is solely responsible for compliance with all applicable laws and regulations when using this software.

The developer and/or supplier of this software shall not be liable for any loss or damage arising out of the unauthorized use of this software. Unauthorized use includes, but is not limited to, using the software for illegal purposes, infringing copyrights, patents, trademarks or other intellectual property rights, or using the software in violation of the license terms of the software.

This software may not be used for illegal or unlawful purposes. Any use of the software for illegal activities, including but not limited to fraud, hacking, privacy violation, distribution of malware or any other actions contrary to the code and regulations is strictly prohibited. The user is fully responsible for any legal consequences arising from the use of this software for illegal purposes.

Your use of this software constitutes your agreement to the terms of this disclaimer. If you do not agree to these terms, you must stop using this software immediately.

## Quick start

### Local VPN (Android 10+)

1. Install an APK built from this fork (see [Building APK](#building-apk-from-source-code)).
2. Remove any manual NoDPI proxy from Android Wi-Fi settings once: set Proxy to None.
3. In **Connection and bypass**, enable **Local VPN** and choose **All apps** or **Selected apps**.
4. Start NoDPI and accept Android's VPN permission dialog.
5. If the network has unusable IPv6, enable **IPv4 only**, apply/reconnect and restart affected apps.

The local VPN applies bypass to all HTTPS domains of captured apps, with the existing
Cloudflare exclusions. There is no second domain-mode switch. Stopping the VPN restores
direct routing unless Android's block-connections-without-VPN policy is enabled. Only
one VPN can be active in a profile; NoDPI itself is excluded to prevent routing loops.

### Manual HTTP proxy

Disable local VPN, start the proxy, and set the Wi-Fi proxy to `127.0.0.1:8881`
(or your configured address/port). Apps must honor that proxy setting. Remove it when
stopping NoDPI; Android does not let an ordinary app edit the saved Wi-Fi proxy automatically.
Domain lists and Standard/All/Auto processing modes apply to this manual-proxy mode.

### Additional Blacklists

The default manual-proxy domain list covers YouTube only. Additional lists can be found [here](https://github.com/GVCoder09/NoDPI/tree/main/blacklists).

## VPN routes, DNS and diagnostics

### External SOCKS5

Configure a server hostname/IP without a URL scheme, port and optional username/password,
then select the apps that should use it. Those apps are automatically included in VPN
capture; other captured apps use local bypass. The home screen shows the applied routes.
Use **Save and apply** in app selection or **Apply and reconnect** for other network changes.

Routing uses the original connection owner UID, not destination IP/domain heuristics.
Shared-UID groups must be selected in full. With external routing enabled, unknown owners
are rejected. An unavailable proxy or authentication failure never falls back directly.
External SOCKS5 currently supports TCP only; external UDP is blocked. SOCKS5 itself does
not encrypt the connection to the proxy. Passwords use Android Keystore encryption and
are excluded from logs; restoring on another device requires entering them again.

### DNS and IPv4-only mode

VPN DNS forwards real wire responses through Cloudflare DoH (`https://1.1.1.1/dns-query`)
or physical-network DNS servers. It no longer generates temporary `198.19.*` addresses.
Old synthetic addresses and unsupported internal DNS probes are rejected immediately;
restart affected apps after upgrading to clear old DNS caches. DoH errors do not silently
fall back to system DNS. External-route apps use this same resolver, not DNS through SOCKS5.
The manual-proxy DoH address resolver requests IPv4 addresses.

**IPv4 only** defaults off. When enabled, VPN DNS returns NOERROR/NODATA for AAAA and
HTTPS/SVCB queries, allowing ordinary A resolution. Cached IPv6 destinations are rejected;
IPv6 routes remain captured so selected traffic cannot bypass the VPN. IPv6-only services
will be unavailable. Apps with their own encrypted DNS may still obtain IPv6 answers,
but captured IPv6 connections remain blocked. Apply/reconnect and restart apps after changing it.

### Resource management and network changes

| Resource | Limit |
|---|---:|
| Shared local/external traffic sessions | 128 |
| Traffic UDP associations | 16 |
| Separate DNS sessions | 16 |
| HTTP proxy connections | 128 |
| Native engine sessions | 256 |

Worker pools are bounded. QUIC associations are closed to encourage HTTPS-over-TCP
fallback; local UDP otherwise has no DPI bypass. Idle UDP associations expire after
30 seconds. TCP tracks activity in both directions and expires after 60 seconds of
inactivity; capacity pressure can reclaim a TCP flow idle for at least 5 seconds.
A full DNS pool can reclaim an already answered idle UDP association after a reply-drain
grace interval. State is rechecked under a lock; in-flight DNS queries are not selected.
Repeated limit failures are aggregated with pool/session/phase/idle diagnostics.
Actual memory, CPU and battery use still need device measurements.

Physical-network changes preserve the TUN interface and applied routes, then reset old
connections for app retry. Events debounce for 750 ms, with up to three update attempts.
Offline state retains VPN capture. Handover does not load pending settings, and late
callbacks cannot restart a stopped service.

### Journal and connection checks

The private no-backup journal retains 500 entries across process restarts. Java crash
stacks are recorded separately where possible; Android 11+ supplies previous process-exit
information. Exported text starts with a timestamped title for receiving apps to name it.

`FRAGMENTED` means processing was applied, not confirmed access. `REMOTE_DATA_RECEIVED`
and `NO_REMOTE_DATA` distinguish destination bytes; generated CONNECT responses are
excluded. A completed TLS handshake is not inferred from encrypted pass-through traffic.

Errors include stage, selected IP/port/family, DNS source, measured DNS/TCP/phase time,
nested cause and errno. A physical-network snapshot records transport, Android validation
and default IPv4/IPv6 routes at failure time, without SSID/subscriber identifiers.
`TIMEOUT_UNDETERMINED` does not claim filtering: a timeout alone cannot distinguish it
from server/network failure, and the network may have changed since connection start.

Explicit diagnostics check proxy DNS → TCP → CONNECT → certificate-verified TLS → HTTP.
Start NoDPI first for local diagnostics. The SOCKS5 server check connects to
`telegram.org:443`; domain diagnostics use the domain entered by the user.

### Settings transfer

Export/import uses the Android document picker and a versioned `.properties` file. It
includes app routes, SOCKS endpoint, DNS/IPv4 and bypass options, and domains. All usernames
and passwords, manual-proxy binding/authentication, autostart and consent are excluded.
Import is validated and confirmed, replaces domains, and does not reconnect the running VPN.
Authenticated SOCKS5 requires credentials again; incomplete authentication is rejected
before replacing a working VPN. Old files without the IPv4 setting default to dual-stack mode.
Limits: 1 MiB, 500 packages per list and 10,000 domains.

## Building APK from source code

1) Install [Android Studio](https://developer.android.com/studio) (or JDK 17+ and the Android SDK with platform 36).
2) Open the project in Android Studio and press *Run*, or build from the command line:
```bash
./gradlew assembleDebug
```
On Windows use `gradlew.bat assembleDebug`. The APK will be in `app/build/outputs/apk/debug/`.
Debug builds are named **NoDPI Test**, use application ID `com.gvcoder.nodpi.test` and can
be installed alongside the main app. Updates require the same signing key.

3) Install it on a device connected via adb:
```bash
./gradlew installDebug
```

Validation:

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug --no-daemon
python3 scripts/test-native-routing.py
```

The pinned HevSocks5Tunnel 2.19.3 source, routing patch, licenses and native build are
documented in [native/README.md](native/README.md). Rebuilding requires NDK 28.2.13676358;
the bundled AAR supports four ABIs with 16 KiB ELF alignment. JNI routing runs on a dedicated
worker and retains its callback under R8. A prebuilt patched AAR is included for app builds.

The [validation workflow](.github/workflows/validation.yml) runs on push/PR: native rebuild,
native harness, JVM tests, debug APK and lint. Lint errors fail validation. APK/reports are
retained for seven days; there is no publishing step. CI's generated debug signing key is
not guaranteed to match a locally installed test APK.

The native harness tests request isolation with a deterministic callback. Real Android UID
ownership, external SOCKS5 with a reachable server, network handovers and sustained background
behavior still require device testing. Native shutdown latency in `onDestroy()` is a remaining
ANR-risk area. Local socket/JVM tests do not replace those checks.

## FAQ

*Q: Is using NoDPI legal in Russia?*

A: Yes, its usage is completely legal as of October 2025. However, you can be held administratively liable for advertising and distributing tools designed to circumvent blocking.

*Q: Is it safe?*

A: NoDPI forwards other apps’ HTTPS without decrypting it. Connection metadata is kept in the private local journal. Optional DoH and external SOCKS5 involve the configured DNS/proxy provider; see [DNS and diagnostics](#dns-and-ipv4-only-mode).

*Q: Does this program mask my traffic? Can my ISP not see which sites I visit?*

A: Local bypass does not hide destination IP addresses from your ISP. External SOCKS5 changes the destination visible to your local network to the proxy server, but SOCKS5 itself does not encrypt that connection. DNS uses the selected local/system or DoH resolver; this is not an anonymity guarantee.

## Star History

[![Star History Chart](https://star-history.dera.page/svg?repos=GVCoder09/NoDPI4Android&type=Date)](https://star-history.dera.page/#GVCoder09/NoDPI4Android&Date)
