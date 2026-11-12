#!/usr/bin/env python3
"""Exercise the exact native route-worker implementation with deterministic JVM callback results."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
source = root / 'native/.build/source/src/hev-jni.c'
if not source.exists():
    raise SystemExit('Build the native engine first: scripts/build-native-vpn.sh')
text = source.read_text()
worker = text[text.index('static pthread_mutex_t route_mutex'):text.rindex('#endif')]
harness = r'''
#include <pthread.h>
#include <string.h>
#include <stdio.h>
#include <assert.h>
#include <stdint.h>
static void *java_vm = (void *)1;
static int call_java_route(int protocol, const char *source, int source_port,
                          const char *destination, int destination_port) {
    assert(protocol == 6 || protocol == 17);
    assert(strcmp(source, "198.18.0.1") == 0 || strcmp(source, "fd00::1") == 0);
    assert(strcmp(destination, "203.0.113.8") == 0 || strcmp(destination, "2001:db8::8") == 0);
    assert(destination_port == 443);
    return source_port == 1001 ? 12001 : source_port == 1002 ? 12002 : -1;
}
'''
tests = r'''
static void *request(void *data) {
    int index = (int)(intptr_t)data;
    int port = index % 2 ? 1001 : 1002;
    int expected = index % 2 ? 12001 : 12002;
    assert(nodpi_select_port(index % 2 ? 6 : 17, "198.18.0.1", port, "203.0.113.8", 443) == expected);
    return NULL;
}
int main(void) {
    pthread_t threads[64];
    for (int i=0; i<64; i++) assert(pthread_create(&threads[i], NULL, request, (void *)(intptr_t)i) == 0);
    for (int i=0; i<64; i++) pthread_join(threads[i], NULL);
    assert(nodpi_select_port(6, "fd00::1", 1001, "2001:db8::8", 443) == 12001);
    assert(nodpi_select_port(17, "fd00::1", 1002, "2001:db8::8", 443) == 12002);
    assert(nodpi_select_port(6, "198.18.0.1", 9999, "203.0.113.8", 443) == -1);
    java_vm = NULL;
    assert(nodpi_select_port(6, "198.18.0.1", 1001, "203.0.113.8", 443) == 0);
    puts("Native routing worker: 64 concurrent same-destination flows, IPv6, rejection, standalone fallback passed");
    return 0;
}
'''
with tempfile.TemporaryDirectory(prefix='nodpi-native-test-') as directory:
    directory = Path(directory)
    cfile, binary = directory/'routing-test.c', directory/'routing-test'
    cfile.write_text(harness + worker + tests)
    subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra', '-pthread', str(cfile), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True, timeout=15)
