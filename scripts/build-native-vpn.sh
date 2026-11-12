#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
ndk="$ANDROID_HOME/ndk/28.2.13676358"
work="$root/native/.build"
mkdir -p "$work"
python3 - "$root/native/hev-socks5-tunnel-2.19.3.tar.xz" <<'PY'
import hashlib,sys
expected='8aaa33b344cab03a5cfc3675b9aaae55c6ee6088d78678ad1fbb44855b5ba570'
assert hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest()==expected,'Native source checksum mismatch'
PY
mkdir -p "$work/source"
    tar -xf "$root/native/hev-socks5-tunnel-2.19.3.tar.xz" -C "$work/source" --strip-components=1
    patch -d "$work/source" -p1 < "$root/native/patches/per-app-routing.patch"
"$ndk/ndk-build" REV_ID=nodpi-routing-v1 NDK_PROJECT_PATH="$work" APP_BUILD_SCRIPT="$work/source/Android.mk" \
    NDK_APPLICATION_MK="$root/native/Application.mk" NDK_OUT="$work/obj" NDK_LIBS_OUT="$work/libs" -j4
python3 - "$root" <<'PY'
import pathlib,sys,zipfile
root=pathlib.Path(sys.argv[1])
source=root/'app/libs/hev-socks5-tunnel-2.19.3.aar'
output=root/'app/libs/hev-socks5-tunnel-2.19.3-nodpi.aar'
with zipfile.ZipFile(source) as old,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as new:
    for name in ['AndroidManifest.xml','classes.jar','proguard.txt']:
        payload=old.read(name)
        if name=='proguard.txt': payload+=b'\n-keep class com.gvcoder.nodpi.core.NativeRouting { public static int selectPort(int, java.lang.String, int, java.lang.String, int); }\n'
        new.writestr(name,payload)
    for path in sorted((root/'native/.build/libs').glob('*/libhev-socks5-tunnel.so')):
        new.write(path,'jni/'+path.parent.name+'/'+path.name)
print(output)
PY
