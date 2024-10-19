package party.jml.partyboi.data

import arrow.core.*
import kotliquery.queryOf
import party.jml.partyboi.AppServices
import java.nio.file.Path

class FixtureLoader(val app: AppServices) {
    val db = app.db

    val tables = listOf(
        "screen",
        "file",
        "entry",
        "compo",
        "session",
        "vote",
        "appuser",
    )

    fun truncateTables(): Either<AppError, Unit> =
        db.transaction { tx ->
            db.use(tx) {
                tables.forEach { table ->
                    it.exec(queryOf("TRUNCATE TABLE $table CASCADE"))
                }
            }
            Unit.right()
        }

    fun loadFixture(resourceName: String): Either<AppError, Unit> =
        loadFixtureResource(resourceName)
            .flatMap { sql -> db.use { it.exec(queryOf(sql)) } }

    fun loadFixtureResource(resourceName: String): Either<AppError, String> =
        FixtureLoader::class.java.getResource(resourceName)
            .toOption()
            .toEither { NotFound("Fixture '${resourceName}' not found") }
            .map { it.readText() }
}