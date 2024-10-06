package party.jml.partyboi.auth

import io.ktor.server.sessions.*
import kotliquery.queryOf
import party.jml.partyboi.data.DatabasePool

class SessionRepository(private val db: DatabasePool) : SessionStorage {
    init {
        db.init("""
            CREATE TABLE IF NOT EXISTS session (
                id text NOT NULL,
                value text NOT NULL
            );
        """)
    }

    override suspend fun invalidate(id: String) {
        db.use { it.execute(queryOf("DELETE FROM session WHERE id = ?", id)) }
    }

    override suspend fun read(id: String): String {
        return db.useUnsafe {
            val query = queryOf("SELECT value FROM session WHERE id = ?", id)
                .map { it.string(1) }
                .asSingle
            it.run(query) ?: throw NoSuchElementException("Session $id not found")
        }
    }

    override suspend fun write(id: String, value: String) {
        db.use { it.execute(queryOf("INSERT INTO session(id, value) VALUES (?, ?)", id, value)) }
    }
}