package party.jml.partyboi

import party.jml.partyboi.database.*

class AppServices(db: DatabasePool) {
    val compos by lazy { CompoRepository(db) }
    val entries by lazy { EntryRepository(db) }
    val users by lazy { UserRepository(db) }
}