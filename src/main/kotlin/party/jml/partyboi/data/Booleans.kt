package party.jml.partyboi.data

inline fun <reified T> Boolean.map(f: () -> T): T? =
    if (this) f() else null