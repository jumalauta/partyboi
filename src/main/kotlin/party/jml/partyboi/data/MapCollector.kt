package party.jml.partyboi.data

class MapCollector<K, V> {
    private val map = mutableMapOf<K, List<V>>()

    fun add(key: K, value: V) {
        map[key] = (map[key] ?: emptyList()) + value
    }

    fun first(key: K): V? = map[key]?.first()

    fun all(key: K): List<V> = map[key] ?: emptyList()
}