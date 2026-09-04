package dm1sh.android_tracker.data

import java.nio.charset.StandardCharsets

/**
 * Deterministic client IDs derived from event content via FNV-1a 64-bit hashing.
 *
 * The same event (same content + timestamp) always produces the same ID,
 * regardless of install state, so the server can deduplicate correctly via
 * its `(device_ref, client_id)` unique constraint.
 */
object ClientId {

    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L

    private fun fnv1a(input: String): Long {
        var hash = FNV_OFFSET_BASIS
        for (b in input.toByteArray(StandardCharsets.UTF_8)) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= FNV_PRIME
        }
        return hash
    }

    /** Deterministic ID for a usage event. */
    fun eventClientId(
        packageName: String,
        eventType: Int,
        className: String?,
        timestamp: Long
    ): Long = fnv1a("$packageName\t$eventType\t${className ?: ""}\t$timestamp")

    /** Deterministic ID for a device-metrics snapshot. */
    fun metricClientId(
        capturedAt: Long,
        batteryLevel: Int,
        storageFreeBytes: Long
    ): Long = fnv1a("$capturedAt\t$batteryLevel\t$storageFreeBytes")
}
