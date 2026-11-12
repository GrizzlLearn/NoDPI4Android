# Proxy core uses only platform APIs; no extra keep rules are required.

# Native flow-owner callback is looked up by its JVM name.
-keep class com.gvcoder.nodpi.core.NativeRouting { public static int selectPort(int, java.lang.String, int, java.lang.String, int); }
