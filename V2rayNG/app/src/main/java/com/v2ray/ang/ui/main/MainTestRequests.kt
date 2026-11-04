package com.v2ray.ang.ui.main

import java.util.UUID

/**
 * Main-thread request ownership. Tap-to-test runs may overlap (each tap is its own test and its
 * result is shown when it arrives); a reply for a request that was stopped must not show.
 */
internal class MainTestRequests {
    data class Bulk(val id: String, val groupId: String)

    private val current = mutableSetOf<String>()
    var bulk: Bulk? = null
        private set
    val isTesting: Boolean get() = current.isNotEmpty() || bulk != null

    fun beginCurrent(): String = UUID.randomUUID().toString().also { current.add(it) }

    fun completeCurrent(id: String): Boolean = current.remove(id)

    fun invalidateCurrent() {
        current.clear()
    }

    fun beginBulk(groupId: String): Bulk = Bulk(UUID.randomUUID().toString(), groupId).also { bulk = it }

    fun completeBulk(id: String): Bulk? {
        val request = bulk?.takeIf { it.id == id } ?: return null
        bulk = null
        return request
    }

    fun cancelBulk() {
        bulk = null
    }
}
