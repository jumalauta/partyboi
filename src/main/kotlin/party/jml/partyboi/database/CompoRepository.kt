package party.jml.partyboi.database

import arrow.core.Either
import kotliquery.Row
import kotliquery.queryOf
import party.jml.partyboi.errors.AppError

class CompoRepository(private val db: DatabasePool) {
    fun getAllCompos(): Either<AppError, List<Compo>> {
        return getRows("select * from compo order by name")
    }

    fun getOpenCompos(): Either<AppError, List<Compo>> {
        return getRows("select * from compo where allow_submit order by name")
    }

    private fun getRows(queryStr: String): Either<AppError, List<Compo>> {
        return db.use {
            val query = queryOf(queryStr)
                .map(Compo.fromRow)
                .asList
            it.run(query)
        }

    }
}

data class Compo(
    val id: Int,
    val name: String,
    val allowSubmit: Boolean,
    val allowVote: Boolean,
) {
    companion object {
        val fromRow: (Row) -> Compo = { row ->
            Compo(
                id = row.int("id"),
                name = row.string("name"),
                allowSubmit = row.boolean("allow_submit"),
                allowVote = row.boolean("allow_vote"),
            )
        }
    }
}