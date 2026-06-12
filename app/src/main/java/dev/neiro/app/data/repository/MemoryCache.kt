package dev.neiro.app.data.repository

/**
 * Simple in-memory TTL cache. Thread-safe via synchronized access.
 * Entries expire after [ttlMs] milliseconds and are evicted on next access.
 */
class MemoryCache<K, V>(private val ttlMs: Long) {
    private val map = mutableMapOf<K, Pair<Long, V>>()

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() - entry.first > ttlMs) {
            map.remove(key)
            return null
        }
        return entry.second
    }

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = System.currentTimeMillis() to value
    }

    @Synchronized
    fun invalidate(key: K) = map.remove(key)

    @Synchronized
    fun clear() = map.clear()
}
