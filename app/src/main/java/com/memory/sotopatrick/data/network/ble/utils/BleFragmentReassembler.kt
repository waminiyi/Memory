package com.memory.sotopatrick.data.network.ble.utils

/**
 * Wrapper that tracks when a reassembly session was created,
 * so stale incomplete sessions can be purged.
 */
private class ReassemblySession(
    val fragments: MutableList<Fragment> = mutableListOf(),
    var expectedTotalFragments: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class BleFragmentReassembler {

    companion object {
        /** Incomplete sessions older than this are considered stale and purged. */
        const val SESSION_TTL_MS = 10_000L
    }

    // Key: MessageId, Value: reassembly session with fragments + creation timestamp
    private val sessions = mutableMapOf<Int, ReassemblySession>()

    fun addFragment(fragment: Fragment): ByteArray? {
        // Purge stale incomplete sessions on every call
        purgeStale()

        // 1. If not fragmented, return payload immediately
        if (fragment.totalFragments == 0) return fragment.payload

        // 2. Get or create the session for this message ID
        val session = sessions.getOrPut(fragment.messageId) { ReassemblySession() }

        // Fragment index 0 indicates a fresh start for this message ID.
        // If we already had partial data for the same ID (e.g. stale fragments from a previous
        // transport session), reset to avoid cross-session payload mixing.
        if (fragment.index == 0 && session.fragments.isNotEmpty()) {
            session.fragments.clear()
            session.expectedTotalFragments = null
        }

        // A valid fragmented message must keep the same totalFragments across all parts.
        val expectedTotal = session.expectedTotalFragments
        if (expectedTotal != null && fragment.totalFragments != expectedTotal) {
            // Conflicting metadata means this session is corrupted; drop and restart from this fragment.
            session.fragments.clear()
            session.expectedTotalFragments = null
        }
        if (session.expectedTotalFragments == null) {
            session.expectedTotalFragments = fragment.totalFragments
        }

        // Ignore impossible indices to avoid accidental completion on malformed packets.
        if (fragment.index < 0 || fragment.index > fragment.totalFragments) {
            return null
        }

        // Avoid adding duplicates if the hardware layer retries
        if (session.fragments.none { it.index == fragment.index }) {
            session.fragments.add(fragment)
        }

        // 3. Check if we have the complete set
        if (session.fragments.size == fragment.totalFragments + 1) {
            val sorted = session.fragments.sortedBy { it.index }
            val hasContiguousSet = sorted.indices.all { idx -> sorted[idx].index == idx }
            if (!hasContiguousSet) {
                // Do not emit malformed payload; wait for a fresh sequence.
                sessions.remove(fragment.messageId)
                return null
            }
            val completeMessage = sorted.flatMap { it.payload.toList() }.toByteArray()

            // 4. Clean up memory for this session
            sessions.remove(fragment.messageId)
            return completeMessage
        }

        // 5. Incomplete: we need to wait for more fragments
        return null
    }

    /**
     * Removes reassembly sessions that have been open longer than [SESSION_TTL_MS].
     * Prevents memory leaks from dropped/incomplete fragment sequences.
     */
    private fun purgeStale() {
        val now = System.currentTimeMillis()
        sessions.entries.removeAll { (_, session) ->
            now - session.createdAt > SESSION_TTL_MS
        }
    }

    fun clear() {
        sessions.clear()
    }
}
