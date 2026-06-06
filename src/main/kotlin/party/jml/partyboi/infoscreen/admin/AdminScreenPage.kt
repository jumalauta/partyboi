package party.jml.partyboi.infoscreen.admin

import kotlinx.html.*
import party.jml.partyboi.assets.Asset
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.nonEmptyString
import party.jml.partyboi.form.*
import party.jml.partyboi.infoscreen.*
import party.jml.partyboi.infoscreen.slides.Slide
import party.jml.partyboi.templates.Javascript
import party.jml.partyboi.templates.NavItem
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.triggers.TriggerRow
import java.util.*

object AdminScreenPage {
    fun renderAdHocForm(
        form: Form<*>,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Screen admin",
        subLinks = generateSubLinks(slideSets),
    ) {
        renderWithScreenMonitoring(false) {
            renderForm(
                url = "/admin/screen/adhoc",
                form = form,
                title = "Ad hoc screen",
                submitButtonLabel = "Show now"
            )
        }
    }

    fun renderSlideSetForms(
        slideSet: String,
        screenState: InfoScreenState,
        isRunning: Boolean,
        slides: List<SlideEditData>,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Screen admin",
        subLinks = generateSubLinks(slideSets)
    ) {
        val willHaltAutoRun = slides.find {
            it.slide is AutoRunHalting && it.slide.haltAutoRun()
        } != null

        div(classes = "title-row") {
            val slideSetName = slideSets.find { it.id == slideSet }?.name ?: "Slide set $slideSet"
            h1 { +slideSetName }
            div(classes = "title-row-actions") {
                a(href = "/admin/screen/new") {
                    attributes["role"] = "button"
                    icon("plus")
                    +" New slide set"
                }
                if (slideSet != SlideSetRow.ADHOC && slideSet != SlideSetRow.DEFAULT) {
                    deleteButton(
                        url = "/admin/screen/slideset/$slideSet",
                        confirmation = confirmDelete("slide set", slideSetName, cascade = "all its slides"),
                        label = "Delete",
                        redirectUrl = "/admin/screen",
                        classes = "primary",
                    )
                }
            }
        }

        renderWithScreenMonitoring(true) {
            reloadSection {
                nav(classes = "slide-set-toolbar") {
                    ul {
                        if (!willHaltAutoRun) {
                            if (screenState.slideSet != slideSet || !isRunning) {
                                li {
                                    val isDisabled = slides.all { !it.visible }
                                    if (isDisabled) {
                                        tooltip("Need at least one slide set visible to activate auto run")
                                    }
                                    postButton("/admin/screen/${slideSet}/start") {
                                        disabled = isDisabled
                                        icon(Icon("play"))
                                        +" Auto run"
                                    }
                                }
                            }
                            if (screenState.slideSet == slideSet && isRunning) {
                                li {
                                    postButton("/admin/screen/${slideSet}/stop") {
                                        icon(Icon("pause"))
                                        +" Pause auto run"
                                    }
                                }
                            }
                        } else {
                            li {
                                postButton("/admin/screen/${slideSet}/next") {
                                    icon(Icon.next)
                                    +" Show next slide"
                                }
                            }
                        }
                    }
                    ul {
                        li {
                            details(classes = "dropdown") {
                                summary { +"Add slide" }
                                ul {
                                    li {
                                        a(href = "/admin/screen/${slideSet}/new/textslide", classes = "flat-button") {
                                            icon(Icon("list-ul"))
                                            +" Normal text slide"
                                        }
                                    }
                                    li {
                                        a(href = "/admin/screen/${slideSet}/new/qrcodeslide", classes = "flat-button") {
                                            icon(Icon("qrcode"))
                                            +" Slide with a QR code"
                                        }
                                    }
                                    li {
                                        a(href = "/admin/screen/${slideSet}/new/imageslide", classes = "flat-button") {
                                            icon(Icon("image"))
                                            +" Full screen images"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                article {
                    id = "slide-list"
                    table {
                        tbody(classes = "sortable") {
                            attributes.put("data-draggable", "tr")
                            attributes.put("data-handle", ".handle")
                            attributes.put("data-callback", "/admin/screen/${slideSet}/runOrder")
                            slides.forEach { slide ->
                                tr {
                                    attributes.put("data-dragid", slide.id.toString())
                                    if (!slide.readOnly) {
                                        td(classes = "handle tight") { icon("grip-vertical") }
                                    } else {
                                        td(classes = "tight") {}
                                    }
                                    td(classes = "tight") {
                                        if (screenState.id == slide.id) {
                                            if (isRunning) {
                                                span { attributes["aria-busy"] = "true" }
                                            } else {
                                                icon("tv")
                                            }
                                        } else {
                                            postButton("/admin/screen/${slideSet}/${slide.id}/show") {
                                                attributes.put("class", "flat-button")
                                                tooltip("Show on screen")
                                                icon("play")
                                            }
                                        }
                                    }
                                    td(classes = "wide") {
                                        if (slide.slide is NonEditable) {
                                            tooltip("This slide cannot be edited")
                                            span(classes = "slide-name") {
                                                title = slide.getName()
                                                val type = slide.slide.getType()
                                                icon(type.icon, type.description)
                                                +" "
                                                +slide.getName()
                                            }
                                        } else {
                                            a(href = "/admin/screen/${slideSet}/${slide.id}", classes = "slide-name") {
                                                title = slide.getName()
                                                val type = slide.slide.getType()
                                                icon(type.icon, type.description)
                                                +" "
                                                +slide.getName()
                                            }
                                        }
                                    }
                                    td(classes = "settings") {
                                        if (!slide.readOnly) {
                                            toggleButton(
                                                slide.visible,
                                                IconSet.visibility,
                                                "/admin/screen/${slide.id}/setVisible"
                                            )
                                            toggleButton(
                                                slide.showOnInfoPage,
                                                IconSet.showOnInfoPage,
                                                "/admin/screen/${slide.id}/showOnInfo"
                                            )
                                            deleteButton(
                                                url = "/admin/screen/${slide.id}",
                                                tooltipText = "Delete ${slide.getName()}",
                                                confirmation = confirmDelete("slide", slide.getName())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        script(src = "/assets/draggable.min.js") {}
    }

    fun renderCompoRunnerPage(
        compos: List<Compo>,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Compo screens",
        subLinks = generateSubLinks(slideSets)
    ) {
        h1 { +"Compo screens" }
        article {
            table {
                thead {
                    tr {
                        th { +"Compo" }
                        th {}
                        th {}
                    }
                }
                tbody {
                    compos.forEach { compo ->
                        tr {
                            td { +compo.displayName }
                            td {
                                a(href = "/admin/compos/${compo.id}/run") {
                                    icon("image")
                                    +" Compo slides"
                                }
                            }
                            td {
                                a(href = "/admin/compos/${compo.id}/run-results") {
                                    icon("square-poll-horizontal")
                                    +" Result slides"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun FlowContent.renderWithScreenMonitoring(refreshOnSlideChange: Boolean, block: FlowContent.() -> Unit) {
        columns({
            block()
        }, {
            div(classes = "screen-preview-container") {
                iframe(classes = "screen-preview") {
                    attributes.put("src", "/screen")
                    attributes.put("width", "1920")
                    attributes.put("height", "1080")
                }
            }
            a(href = "/screen", target = "_blank") {
                +"Open in new tab"
            }
        })
        if (refreshOnSlideChange) {
            script(src = "/assets/refreshOnSlideChange.js") {}
        }
    }

    fun renderNewSlideForm(
        slideSet: String,
        slide: Slide<*>,
        errors: AppError? = null,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "New slide",
        subLinks = generateSubLinks(slideSets),
    ) {
        h1 { +"New slide / ${slideSets.find { it.id == slideSet }?.name ?: "Slide set $slideSet"}" }
        val form = slide.getForm()
        renderForm(
            url = "/admin/screen/${slideSet}/new/${slide.javaClass.simpleName.lowercase()}",
            form = if (errors == null) form else form.with(errors),
            submitButtonLabel = "Create slide",
        )
    }

    fun renderNewSlideSetForm(
        form: Form<NewSlideSet>,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "New slide set",
        subLinks = generateSubLinks(slideSets),
    ) {
        h1 { +"New slide set" }
        renderForm(
            url = "/admin/screen/new",
            form = form,
            submitButtonLabel = "Create slide set",
        )
    }

    // Picker for creating many image slides at once. Lists image assets that aren't
    // yet referenced by an ImageSlide in this slide set, plus an embedded asset
    // upload form so new images can be added without leaving the picker.
    fun renderImageSlidesPicker(
        slideSet: String,
        availableImages: List<Asset>,
        uploadError: String? = null,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Add image slides",
        subLinks = generateSubLinks(slideSets),
    ) {
        h1 { +"Add image slides / ${slideSets.find { it.id == slideSet }?.name ?: "Slide set $slideSet"}" }

        form(
            action = "/admin/screen/${slideSet}/new/imageslide/upload",
            method = FormMethod.post,
            encType = FormEncType.multipartFormData,
        ) {
            article {
                header { +"Upload images" }
                fieldSet {
                    if (uploadError != null) {
                        section(classes = "error") { +uploadError }
                    }
                    label {
                        span { +"Upload files" }
                        fileInput(name = "files") {
                            multiple = true
                        }
                    }
                }
                submitButton("Upload")
            }
        }

        form(
            action = "/admin/screen/${slideSet}/new/imageslide",
            method = FormMethod.post,
        ) {
            article {
                header { +"Pick images to add as slides" }
                if (availableImages.isEmpty()) {
                    p { +"No unused images — upload some above." }
                } else {
                    button(type = ButtonType.button, classes = "secondary outline") {
                        id = "select-all-images"
                        +"Select all"
                    }
                    div(classes = "image-picker-grid") {
                        availableImages.forEach { asset ->
                            label(classes = "image-picker-item") {
                                checkBoxInput(name = "assetImage") {
                                    value = asset.fullName
                                }
                                img(src = "/assets/${asset.fullName}", alt = asset.displayName)
                                span { +asset.displayName }
                            }
                        }
                    }
                    submitButton("Create slides")
                }
            }
        }

        // Toggle all checkboxes from a single button: clicking when any is unchecked
        // selects all; clicking again (when all are checked) clears the selection.
        script {
            unsafe {
                +"""
                document.getElementById('select-all-images')?.addEventListener('click', () => {
                    const boxes = document.querySelectorAll('input[name="assetImage"]');
                    const allChecked = [...boxes].every(b => b.checked);
                    boxes.forEach(b => { b.checked = !allChecked; });
                });
                """.trimIndent()
            }
        }
    }

    fun renderSlideForm(
        slideSet: String,
        slide: SlideEditData,
        triggers: List<TriggerRow>,
        assetImages: List<Asset>,
        errors: AppError? = null,
        slideSets: List<SlideSetRow>,
    ) = Page(
        title = "Edit slide",
        subLinks = generateSubLinks(slideSets),
    ) {
        h1 { +"${slide.getName()} / ${slideSets.find { it.id == slideSet }?.name ?: "Slide set $slideSet"}" }
        val form = slide.slide.getForm()
        if (slide.readOnly) {
            article { renderReadonlyFields(form) }
        } else {
            renderForm(
                url = "/admin/screen/${slideSet}/${slide.id}/${slide.slide.javaClass.simpleName.lowercase()}",
                form = if (errors == null) form else form.with(errors),
                submitButtonLabel = "Save changes",
                options = mapOf("assetImage" to assetImages.map(DropdownOption.fromAsset))
            )
        }

        if (triggers.isNotEmpty()) {
            article {
                header { +"When this slide is shown the following actions are executed automatically" }
                ul { triggers.forEach { li { +it.description } } }
            }
        }
    }

    private fun generateSubLinks(slideSets: List<SlideSetRow>): List<NavItem> =
        slideSets.map { it.toNavItem() } + listOf(
            NavItem(
                url = "/admin/screen/compos",
                label = "Compos",
            )
        )
}

fun FlowContent.postButton(url: String, block: BUTTON.() -> Unit) {
    button {
        onClick = Javascript.build {
            httpPost(url)
            refresh()
        }
        block()
    }
}

data class SlideEditData(
    val id: UUID,
    val visible: Boolean,
    val showOnInfoPage: Boolean,
    val slide: Slide<*>,
    val readOnly: Boolean,
) {
    fun getName(): String =
        slide.getName().nonEmptyString() ?: "New slide"

    companion object {
        val fromRow: (SlideRow) -> SlideEditData = { row ->
            SlideEditData(
                id = row.id,
                visible = row.visible,
                showOnInfoPage = row.showOnInfoPage,
                slide = row.getSlide(),
                readOnly = row.readOnly,
            )
        }
    }
}
