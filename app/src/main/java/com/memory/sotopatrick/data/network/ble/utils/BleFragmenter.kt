package com.memory.sotopatrick.data.network.ble.utils

import com.memory.sotopatrick.data.network.ble.BLEConstants
import com.memory.sotopatrick.data.network.ble.utils.Fragment

class BleFragmenter(
    // Max payload per fragment. The encoded packet = 3-byte header + payload,
    // and the BLE ATT layer adds another 3-byte overhead to each notification.
    // So usable payload = MTU - 3 (ATT) - 3 (fragment header) = MTU - 6.
    private val maxFragmentSize: Int = BLEConstants.MAX_MTU - 6
) {
    /**
     * Slices a ByteArray into fragments, all linked by a common [messageId].
     */
    fun fragment(data: ByteArray, messageId: Int): List<Fragment> {
        if (data.size <= maxFragmentSize) {
            return listOf(
                Fragment(
                    messageId = messageId,
                    index = 0,
                    totalFragments = 0,
                    payload = data
                )
            )
        }

        val chunks = data.toList().chunked(maxFragmentSize)
        return chunks.mapIndexed { index, chunk ->
            Fragment(
                messageId = messageId,
                index = index,
                totalFragments = chunks.size - 1,
                payload = chunk.toByteArray()
            )
        }
    }

    /**
     * Encodes a fragment into a raw ByteArray with a 3-byte header.
     * Protocol: [Byte 0: MsgId] [Byte 1: Index] [Byte 2: Total] [Bytes 3+: Payload]
     */
    fun encode(fragment: Fragment): ByteArray {
        val header = byteArrayOf(
            (fragment.messageId and 0xFF).toByte(),
            (fragment.index and 0xFF).toByte(),
            (fragment.totalFragments and 0xFF).toByte()
        )
        return header + fragment.payload
    }

    /**
     * Decodes a raw ByteArray back into a Fragment object.
     */
    fun decode(data: ByteArray): Fragment? {
        // We now expect at least 3 bytes for the header
        if (data.size < 3) return null

        return Fragment(
            messageId = data[0].toInt() and 0xFF,
            index = data[1].toInt() and 0xFF,
            totalFragments = data[2].toInt() and 0xFF,
            payload = data.copyOfRange(3, data.size)
        )
    }
}