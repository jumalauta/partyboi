package party.jml.partyboi.auth

import party.jml.partyboi.AppServices
import party.jml.partyboi.data.randomStringId
import party.jml.partyboi.db.DbBasicMappers.asInt
import party.jml.partyboi.db.DbBasicMappers.asString
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.system.AppResult

class PasswordResetRepository(val app: AppServices) {
    private val db = app.db

    suspend fun generatePasswordResetCode(userId: Int): AppResult<String> = db.use {
        val code = randomStringId(32)
        it.one(
            queryOf(
                "INSERT INTO password_reset(code, user_id) VALUES (?, ?) RETURNING code",
                code,
                userId
            ).map(asString)
        )
    }

    suspend fun getPasswordResetUserId(code: String): AppResult<Int> = db.use {
        it.one(
            queryOf(
                """
                SELECT user_id 
                FROM password_reset 
                WHERE code = ?
                  AND expires_at > now()""",
                code
            ).map(asInt)
        )
    }

    suspend fun invalidateCode(code: String): AppResult<Unit> = db.use {
        it.exec(queryOf("DELETE FROM password_reset WHERE code = ?", code))
    }
}