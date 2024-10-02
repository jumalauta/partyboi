package party.jml.partyboi.data

import arrow.core.Either

fun <A> Either<A, A>.getAny(): A = fold({ it }, { it })
