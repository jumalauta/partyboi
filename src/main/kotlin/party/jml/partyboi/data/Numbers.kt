package party.jml.partyboi.data

import arrow.core.None
import arrow.core.Option
import arrow.core.Some

object Numbers {
    fun positiveInt(a: Int): Option<Int> = if (a >= 0) Some(a) else None
}