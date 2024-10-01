package party.jml.partyboi

import party.jml.partyboi.database.*

class AppServices(db: DatabasePool) {
    val sessions = SessionRepository(db)
    val compos = CompoRepository(db)
    val entries = EntryRepository(db)
    val users = UserRepository(db)
}