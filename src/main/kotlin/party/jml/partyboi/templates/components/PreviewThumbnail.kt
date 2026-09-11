package party.jml.partyboi.templates.components

import kotlinx.html.FlowContent
import kotlinx.html.figure
import kotlinx.html.i

/**
 * Clickable entry preview thumbnail. Clicking opens the full preview (image, video or
 * audio snippet) in the modal that partyboi.js wires to any element with a
 * data-preview-url attribute. Renders an empty placeholder figure when there is no preview.
 */
fun FlowContent.previewThumbnail(preview: party.jml.partyboi.entries.Preview?) {
    if (preview == null) {
        figure {}
        return
    }
    val isVideo = preview.previewFileIsVideo
    val hasAudio = preview.previewAudioFilePath != null
    val classes = buildString {
        append("clickable-preview")
        if (hasAudio) append(" has-audio")
    }
    figure(classes = classes) {
        attributes["style"] = "background-image: url(${preview.externalUrl()})"
        attributes["data-preview-url"] = preview.externalPreviewFileUrl()
        attributes["data-preview-type"] = if (isVideo) "video" else "image"
        if (hasAudio) {
            attributes["data-preview-audio-url"] = preview.externalPreviewAudioFileUrl()
        }
        attributes["role"] = "button"
        attributes["tabindex"] = "0"
        attributes["aria-label"] = if (hasAudio) "Play audio preview" else "Open full-size preview"
        if (hasAudio) {
            i(classes = "fa-solid fa-circle-play play-overlay") {}
        }
    }
}
