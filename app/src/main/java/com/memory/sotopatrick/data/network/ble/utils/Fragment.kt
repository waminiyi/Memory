package com.memory.sotopatrick.data.network.ble.utils

data class Fragment(
    val messageId: Int,
    val index: Int,
    val totalFragments: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Fragment

        if (messageId != other.messageId) return false
        if (index != other.index) return false
        if (totalFragments != other.totalFragments) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId
        result = 31 * result + index
        result = 31 * result + totalFragments
        result = 31 * result + payload.contentHashCode()
        return result
    }
}