package party.jml.partyboi.entries

import arrow.core.Either
import arrow.core.raise.either
import party.jml.partyboi.AppServices
import party.jml.partyboi.database.EntryUpdate
import party.jml.partyboi.errors.AppError
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.Renderable

object EditEntryPage {
    fun render(app: AppServices, formData: Form<EntryUpdate>): Either<AppError, Renderable> = either {
        val compos = app.compos.getAllCompos().bind()
        Page("Edit entry") {
            editEntryForm("/entries/${formData.data.id}", compos.filter { it.allowSubmit }, formData)
        }
    }

}