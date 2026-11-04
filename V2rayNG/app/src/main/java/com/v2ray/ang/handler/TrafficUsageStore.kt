package com.v2ray.ang.handler

import com.tencent.mmkv.MMKV
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Persistent proxy-traffic history, written by the daemon and read by the usage screen.
 *
 * Keys (values are "up,down" byte counts):
 * - `d|yyyyMMdd|<guid>`: per-config total for one day
 * - `h|yyyyMMddHH`: all-config total for one hour, used for the "today" chart
 *
 * Writes are buffered in memory and flushed every [FLUSH_INTERVAL_MS] so the per-second
 * stats loop does not hit storage on every tick.
 */
object TrafficUsageStore {
    private const val ID_TRAFFIC_USAGE = "TRAFFIC_USAGE"
    private const val FLUSH_INTERVAL_MS = 10_000L
    private const val KEEP_DAYS = 60L
    private const val HOURLY_KEEP_DAYS = 2L

    private val storage by lazy { MMKV.mmkvWithID(ID_TRAFFIC_USAGE, MMKV.MULTI_PROCESS_MODE) }

    private val dayFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val hourFormat = DateTimeFormatter.ofPattern("yyyyMMddHH")

    private val pending = HashMap<String, LongArray>()
    private var lastFlush = 0L
    private var lastPruneDay = ""

    data class Usage(val up: Long, val down: Long) {
        val total: Long get() = up + down
        operator fun plus(other: Usage) = Usage(up + other.up, down + other.down)

        companion object {
            val ZERO = Usage(0, 0)
        }
    }

    /** Adds proxied bytes for [guid] at [nowMillis]. Called from the daemon's stats loop. */
    @Synchronized
    fun record(guid: String?, up: Long, down: Long, nowMillis: Long = System.currentTimeMillis()) {
        if (up <= 0 && down <= 0) {
            maybeFlush(nowMillis)
            return
        }
        val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val day = time.format(dayFormat)
        add("d|$day|${guid.orEmpty()}", up, down)
        add("h|${time.format(hourFormat)}", up, down)
        maybeFlush(nowMillis)
    }

    /** Writes buffered counts now, e.g. when the core stops. */
    @Synchronized
    fun flush() {
        if (pending.isEmpty()) return
        pending.forEach { (key, value) ->
            val stored = parse(storage.decodeString(key))
            storage.encode(key, "${stored.up + value[0]},${stored.down + value[1]}")
        }
        pending.clear()
        lastFlush = System.currentTimeMillis()
        pruneOncePerDay()
    }

    /** Totals per hour of today, 24 entries. */
    fun hourlyToday(): List<Usage> {
        val prefix = "h|" + LocalDate.now().format(dayFormat)
        return (0 until 24).map { hour -> read("$prefix${"%02d".format(hour)}") }
    }

    /** Totals per day for the last [days] days, oldest first, today last. */
    fun dailyTotals(days: Int): List<Pair<LocalDate, Usage>> {
        val today = LocalDate.now()
        val byDay = HashMap<String, Usage>()
        dayKeys().forEach { key ->
            val day = key.split('|').getOrNull(1) ?: return@forEach
            byDay[day] = (byDay[day] ?: Usage.ZERO) + read(key)
        }
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to (byDay[date.format(dayFormat)] ?: Usage.ZERO)
        }
    }

    /** Per-config totals since [fromDate] (inclusive), keyed by server GUID. */
    fun perConfig(fromDate: LocalDate): Map<String, Usage> {
        val from = fromDate.format(dayFormat)
        val result = HashMap<String, Usage>()
        dayKeys().forEach { key ->
            val parts = key.split('|', limit = 3)
            if (parts.size != 3 || parts[1] < from) return@forEach
            result[parts[2]] = (result[parts[2]] ?: Usage.ZERO) + read(key)
        }
        return result
    }

    private fun add(key: String, up: Long, down: Long) {
        val slot = pending.getOrPut(key) { LongArray(2) }
        slot[0] += up
        slot[1] += down
    }

    private fun maybeFlush(nowMillis: Long) {
        if (nowMillis - lastFlush >= FLUSH_INTERVAL_MS) flush()
    }

    @Synchronized
    private fun read(key: String): Usage {
        val stored = parse(storage.decodeString(key))
        val buffered = pending[key] ?: return stored
        return Usage(stored.up + buffered[0], stored.down + buffered[1])
    }

    private fun dayKeys(): List<String> =
        storage.allKeys()?.filter { it.startsWith("d|") }.orEmpty()

    private fun pruneOncePerDay() {
        val today = LocalDate.now()
        val todayKey = today.format(dayFormat)
        if (lastPruneDay == todayKey) return
        lastPruneDay = todayKey
        val dayCutoff = today.minusDays(KEEP_DAYS).format(dayFormat)
        val hourCutoff = today.minusDays(HOURLY_KEEP_DAYS).format(dayFormat)
        storage.allKeys()?.forEach { key ->
            val stamp = key.split('|').getOrNull(1) ?: return@forEach
            val expired = when {
                key.startsWith("d|") -> stamp < dayCutoff
                key.startsWith("h|") -> stamp.take(8) < hourCutoff
                else -> false
            }
            if (expired) storage.removeValueForKey(key)
        }
    }

    private fun parse(value: String?): Usage {
        if (value.isNullOrEmpty()) return Usage.ZERO
        val parts = value.split(',')
        return Usage(parts.getOrNull(0)?.toLongOrNull() ?: 0, parts.getOrNull(1)?.toLongOrNull() ?: 0)
    }
}
