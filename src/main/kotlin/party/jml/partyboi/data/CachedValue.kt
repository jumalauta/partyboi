package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount

interface ICachedValue<T> {
    suspend fun get(): Either<AppError, T>
    suspend fun set(value: T): Either<AppError, Unit>
    suspend fun refresh(): Either<AppError, T>

    fun getSync(): Either<AppError, T> = runBlocking { get() }
    fun setSync(value: T): Either<AppError, Unit> = runBlocking { set(value) }
    fun refreshSync(): Either<AppError, T> = runBlocking { refresh() }
}

class CachedValue<T>(
    val ttl: TemporalAmount = java.time.Duration.of(1, ChronoUnit.HOURS),
    val fetchValue: () -> Either<AppError, T>
) :
    ICachedValue<T> {
    private val state = MutableStateFlow<Value<T>?>(null)

    override suspend fun get(): Either<AppError, T> {
        val value = state.value
        return if (value == null || value.isExpired(ttl)) {
            refresh()
        } else {
            value.data.right()
        }
    }

    override suspend fun set(value: T): Either<AppError, Unit> =
        state.emit(Value(value)).right()

    override suspend fun refresh(): Either<AppError, T> = either {
        val value = Value(fetchValue().bind())
        state.emit(value)
        return value.data.right()
    }

    data class Value<A>(
        val data: A,
        val updatedAt: LocalDateTime = LocalDateTime.now(),
    ) {
        fun isExpired(ttl: TemporalAmount): Boolean =
            updatedAt
                .plus(ttl)
                .isBefore(LocalDateTime.now())
    }
}

class PersistentCachedValue<T>(
    ttl: TemporalAmount = java.time.Duration.of(1, ChronoUnit.HOURS),
    fetchValue: () -> Either<AppError, T>,
    val storeValue: (T) -> Either<AppError, Unit>
) : ICachedValue<T> {
    private val cache = CachedValue(ttl, fetchValue)

    override suspend fun get(): Either<AppError, T> =
        cache.get()

    override suspend fun set(value: T): Either<AppError, Unit> =
        if (cache.get().fold({ true }) { it != value }) {
            storeValue(value).onRight { cache.set(value) }
        } else {
            Unit.right()
        }

    override suspend fun refresh(): Either<AppError, T> =
        cache.refresh().onRight { storeValue(it) }
}