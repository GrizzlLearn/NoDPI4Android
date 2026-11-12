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
    - [Additional Blacklists](#additional-blacklists)
  - [Building APK from source code](#building-apk-from-source-code)
  - [FAQ](#faq)
  - [Star History](#star-history)

## Description
  NoDPI4Android is a graphical add-on [NoDPI](https://github.com/GVCoder09/NoDPI). This application is designed to run on Android devices only, Windows and Linux version available [here](https://github.com/GVCoder09/NoDPI/releases)

  NoDPI is a utility designed to bypass DPI (Deep Packet Inspection). What is DPI? DPI is a technology for inspecting network packets based on their content to regulate and filter traffic. It is often used by internet service providers to censor and block access to certain web resources and protocols. The goal of this program is to "trick" DPI by modifying network packets, although in reality, this trickery is often simply a consequence of a lack of computing resources and time. 
 
  This utility was developed primarily for Russian users as a simpler (but less powerful) replacement for similar tools. In most cases, it gets the job done; in particular, it allows you to use YouTube without any problems.

  But unfortunately, the absolute performance of this utility cannot be guaranteed. Some sites, such as YouTube, are good to unlock, while others, as Instagram.com or Facebook.com, require completely different methods for bypassing locks, which are not yet implemented in this utility from their low-leveling and difficulty. Sometimes, the performance of certain sites can also depend on the provider - how it blocks the site and what technical means applies.

<div align="center">
  <p>
    <img src="./assets/screenshot.png" height="600px"/>
  </p>
</div>

### How does this work

NoDPI launches a proxy server on your device through which you direct http(s) traffic. The program intercepts ClientHello of outing connections and fragments them according to the following method:

The packet contains a field that holds the SNI record. The ClientHello message is split into several TLS records: the portion preceding the SNI, the portion containing the SNI (character by character), and the portion following the SNI. A header indicating the ClientHello type is added to each of these parts. Unless out-of-order packet handling (disordering) is disabled, the first of these parts is sent with a TTL=1, causing it to be dropped by the nearest network device. As a result, the DPI (Deep Packet Inspection) system fails to identify the domain for which the connection is being established and allows the connection to proceed. The operating system subsequently retransmits the first part with a standard TTL.

Also, the TLS version is replaced by version 1.3, which is the most modern (but this does not mean that your data begins to be transmitted according to the specifications of this version). All this together allows you to get around the lock.

> [!NOTE]
> Sites hosted on Cloudflare are sent without fragmentation. DPI treats connections to Cloudflare differently: a blocked domain is dropped even when fragmented, and a connection whose domain DPI could not read is frozen after the first ~16 KB. Fragmentation cannot unblock such sites and only breaks the allowed ones.

> [!IMPORTANT]
> NoDPI only works with HTTPS traffic. He can also take HTTP traffic, but purely for compatibility - sites working through this outdated protocol are practically not subject to unlock and the program simply sends traffic to the addressee.

NoDPI does not collect and does not send any data about you, does not use any third-party programs, does not interfere in the system processes and does not require root rights to work.

The app is written in Kotlin with Jetpack Compose (Material 3 Expressive). The proxy runs in a foreground service and can be toggled from a quick settings tile.

### Sites currently unavailable via NoDPI

As of October 2026, the following services and websites cannot be unblocked using NoDPI:
- Telegram
- WhatsApp 
- Voice and video in Discord
- Blocked sites hosted on Cloudflare

All sites blocked by IP address are also unavailable.

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

The application requires Android 7.0 and above.
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

1) Download the latest version from [the Releases page](https://github.com/GVCoder09/NoDPI4Android/releases) and install it
2) Start the proxy with the power button on the main screen
3) In the Wi-Fi settings of Android set the proxy to manual, host 127.0.0.1, port 8881
4) Enjoy!

Please report any problems and malfunctions to us on [the Issues page](https://github.com/GVCoder09/NoDPI4Android/issues)

### Additional Blacklists

By default, a blacklist containing YouTube-only domains is used. Additional lists can be found [here](https://github.com/GVCoder09/NoDPI/tree/main/blacklists).

## Building APK from source code

1) Install [Android Studio](https://developer.android.com/studio) (or JDK 17+ and the Android SDK with platform 36).
2) Open the project in Android Studio and press *Run*, or build from the command line:
```bash
./gradlew assembleDebug
```
On Windows use `gradlew.bat assembleDebug`. The APK will be in `app/build/outputs/apk/debug/`.

3) Install it on a device connected via adb:
```bash
./gradlew installDebug
```

Unit tests of the fragmentation and blacklist logic: `./gradlew testDebugUnitTest`.

## FAQ

*Q: Is using NoDPI legal in Russia?*

A: Yes, its usage is completely legal as of October 2025. However, you can be held administratively liable for advertising and distributing tools designed to circumvent blocking.

*Q: Is it safe?*

A: Yes, using this utility is safe. All traffic sent over HTTPS remains encrypted as it was before. Furthermore, we do not collect any user information.

*Q: Does this program mask my traffic? Can my ISP not see which sites I visit?*

A: No. Unlike a VPN or Tor, this program does not hide your traffic from your Internet Service Provider (ISP). Your ISP can still see the IP addresses of the websites you connect to.

## Star History

[![Star History Chart](https://star-history.dera.page/svg?repos=GVCoder09/NoDPI4Android&type=Date)](https://star-history.dera.page/#GVCoder09/NoDPI4Android&Date)

## Local VPN (this fork)

Android10+ supports local VPN; Android7–9 retains manual HTTP proxy mode. In
Connection and bypass, choose all apps or selected apps. Their HTTPS uses local
bypass for all domains (existing Cloudflare exclusions remain). Save and apply in
the app picker reconnects a running VPN; other changes use Apply and reconnect.
Remove the manual Wi-Fi proxy once. This occupies the profile's single VPN slot.
Do not enable block-connections-without-VPN if direct access is needed after stopping.

### External SOCKS5

Configure a hostname/IP, port and optional username/password, then choose external-route
apps. They are automatically included in VPN capture. Other captured apps retain local
bypass. Shared-UID groups must be selected in full; otherwise external routing refuses startup.
Passwords use Android Keystore encryption, never logs; restore on a new device requires
re-entering them. SOCKS5 itself does not encrypt transport to the proxy.

Routing uses original flow ownership, not destination heuristics. The patched
HevSocks5Tunnel2.19.3 and reproducible build are documented in `native/README.md`.
Unknown owners fail closed when external routing is enabled. External failures never
fall back directly. External SOCKS5 currently supports TCP; external UDP is blocked.
An actually reachable external server is required; IP-blocked proxies cannot work.

### DNS, diagnostics and resource limits

VPN DNS returns real addresses via Cloudflare wire DoH or underlying non-VPN network DNS.
It no longer emits temporary198.19/16 addresses. DNS for external apps uses the same
chosen resolver, not remote SOCKS DNS. Old synthetic addresses and internal DNS:853
fail immediately; restart affected apps once after upgrading to refresh caches.
The manual-proxy DoH address resolver retains its IPv4-only behavior.

Home displays configured/applied app routes, without claiming reachability. Explicit
diagnostics check proxy DNS, proxy TCP, CONNECT, certificate-verified TLS and an HTTP
response. Local diagnostics require NoDPI running. SOCKS server testing connects to
telegram.org:443. FRAGMENTED logs mean processing applied; remote-data receipt is a
separate status and excludes generated CONNECT response bytes.

Traffic bridges share128 session slots, at most16 occupied by UDP. DNS has16 separate slots. QUIC associations close immediately; UDP associations with no outbound packets close after30seconds. HTTP proxy has128 client slots. Worker pools are
bounded. Local UDP has no DPI bypass; UDP/443 is dropped for TCP fallback. Apps without
fallback may fail. The private no-backup journal retains500 entries. Android11+ previous
process-exit information and a separate Java crash record aid diagnostics. Device tests
are required to validate real Android UID ownership and sustained background behavior.

### Network changes, settings transfer and CI

Physical-network changes retain the VPN interface and applied app routes, then reset
old sessions for application retry. Changes debounce750ms with up to3 update attempts.
Offline state keeps VPN capture; pending settings are not loaded on handover and late
callbacks cannot restart a stopped service.

Settings transfer uses the Android document picker and a versioned `.properties` file.
It includes app routes, SOCKS endpoint, bypass options and domains, excluding all usernames,
passwords, manual-proxy binding/authentication, autostart and consent. Import is validated,
confirmed, replaces domains, and does not reconnect a running VPN. Authenticated SOCKS
requires credentials again before startup; invalid routes do not replace a running VPN.
Limits are1MiB,500packages per list and10000domains.

The push/PR validation workflow rebuilds the pinned native source, runs the native harness,
unit tests, debug build and lint with errors enforced. APK/reports are retained7days;
there is no deployment or publishing step.

TCP bridge flows expire after60seconds with no traffic in either direction; capacity pressure first reclaims a TCP flow idle for at least5seconds. DNS UDP associations expire shortly after becoming idle following a reply. LIMIT diagnostics include pool identity, session counts, phases and idle age.

### IPv4-only VPN mode

The optional IPv4 only switch suppresses AAAA and HTTPS/SVCB DNS answers with
NOERROR/NODATA and immediately rejects cached IPv6 destinations. IPv6 routes remain
captured to prevent bypass. Apply/reconnect and restart affected apps to clear caches.
IPv6-only services cannot work; apps using their own encrypted DNS can still obtain
IPv6 answers, but captured IPv6 destinations remain blocked. The setting defaults off,
is included in settings transfer, and old exports without it import as ordinary dual-stack
mode. Manual HTTP proxy mode is unaffected.

DNS pool pressure can reclaim a completed UDP association before periodic cleanup, after a short reply-drain grace interval. State is rechecked while synchronized with query-start; in-flight DNS queries are never selected. The DNS limit remains16.

Connection failures include selected IP/port/family, DNS source, measured DNS/TCP and phase
timing, nested causes and known errno. The physical-network snapshot is taken on failure
and includes transport, Android validation and default IPv4/IPv6 routes; it is not a probe
of that destination. TIMEOUT_UNDETERMINED explicitly cannot distinguish filtering from
server/network failure. A handover may have occurred since connection start.
