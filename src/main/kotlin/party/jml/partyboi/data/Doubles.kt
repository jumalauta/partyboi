package party.jml.partyboi.data

import kotlin.math.roundToInt

fun Double.toPercentage() = "${(this * 100).roundToInt()}%"