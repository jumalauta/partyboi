package party.jml.partyboi.entries

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.io.File

suspend fun ApplicationCall.respondNamedFileDownload(file: File, name: String? = null) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(
            ContentDisposition.Parameters.FileName,
            name ?: file.name
        ).toString()
    )
    respondFile(file)
}

suspend fun ApplicationCall.respondFileShow(file: File, name: String? = null) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Inline.withParameter(
            ContentDisposition.Parameters.FileName,
            name ?: file.name
        ).toString()
    )
    respondFile(file)
}