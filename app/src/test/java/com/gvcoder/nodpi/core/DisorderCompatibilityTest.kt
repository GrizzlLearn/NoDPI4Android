package com.gvcoder.nodpi.core

import java.net.ServerSocket
import java.net.Socket
import java.io.DataInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DisorderCompatibilityTest {
    @Test fun legacyApiFallbackPreservesBothFragmentsWithoutCallingNativeSocketOptions() {
        // Android stub SDK_INT is 0 in local tests: exercises the API < 26 branch.
        ServerSocket(0).use { listener ->
            Socket("127.0.0.1", listener.localPort).use { sender ->
                listener.accept().use { receiver ->
                    receiver.soTimeout = 3000
                    Disorder.send(sender, byteArrayOf(1, 2), byteArrayOf(3, 4))
                    val actual = ByteArray(4)
                    DataInputStream(receiver.getInputStream()).readFully(actual)
                    assertArrayEquals(byteArrayOf(1, 2, 3, 4), actual)
                }
            }
        }
    }
}
