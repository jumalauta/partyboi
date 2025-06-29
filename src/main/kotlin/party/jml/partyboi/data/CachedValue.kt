package party.jml.partyboi.data

import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import party.jml.partyboi.system.AppResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

interface ICachedValue<T> {
    suspend fun get(): AppResult<T>
    suspend fun getOrNull(): T? = get().getOrNull()
    suspend fun set(value: T): AppResult<Unit>
    suspend fun refresh(): AppResult<T>
}

class CachedValue<T>(
    val ttl: Duration = 1.hours,
    val fetchValue: suspend () -> AppResult<T>
) :
    ICachedValue<T> {
    private val state = MutableStateFlow<Value<T>?>(null)

    override suspend fun get(): AppResult<T> {
        val value = state.value
        return if (value == null || value.isExpired(ttl)) {
            refresh()
        } else {
            value.data.right()
        }
    }

    override suspend fun set(value: T): AppResult<Unit> =
        state.emit(Value(value)).right()

    override suspend fun refresh(): AppResult<T> = either {
        val value = Value(fetchValue().bind())
        state.emit(value)
        return value.data.right()
    }

    data class Value<A>(
        val data: A,
        val updatedAt: Instant = Clock.System.now(),
    ) {
        fun isExpired(ttl: Duration): Boolean =
            updatedAt.plus(ttl) < Clock.System.now()
    }
}

class PersistentCachedValue<T>(
    ttl: Duration = 1.hours,
    fetchValue: suspend () -> AppResult<T>,
    val storeValue: suspend (T) -> AppResult<Unit>
) : ICachedValue<T> {
    private val cache = CachedValue(ttl, fetchValue)

    override suspend fun get(): AppResult<T> =
        cache.get()

    override suspend fun set(value: T): AppResult<Unit> =
        if (cache.get().fold({ true }) { it != value }) {
            storeValue(value).onRight { cache.set(value) }
        } else {
            Unit.right()
        }

    override suspend fun refresh(): AppResult<T> =
        cache.refresh().onRight { storeValue(it) }
}