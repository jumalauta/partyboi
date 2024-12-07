package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.right

fun <A> Either<A, A>.getAny(): A = fold({ it }, { it })

class EitherCache<K, E, A> {
    private val cache: MutableMap<K, A> = mutableMapOf()

    fun memoize(key: K, f: () -> Either<E, A>): Either<E, A> {
        val cached = cache[key]
        return if (cached == null) {
            val value = f()
            value.onRight { cache[key] = it }
            value
        } else {
            cached.right()
        }
    }
}

fun <A : AppError, B> Either<A, B>.throwOnError() = onLeft { throw it.throwable ?: Error(it.message) }