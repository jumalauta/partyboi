package party.jml.partyboi.partytemplate

import arrow.core.raise.either
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.compos.GeneralRules
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.Custom
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FileUpload
import party.jml.partyboi.form.Hidden
import party.jml.partyboi.infoscreen.SlideSetRow
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.validation.Validateable
import java.util.*
import kotlin.time.Clock
import kotlin.time.Instant

class PartyTemplateService(app: AppServices) : Service(app) {

    suspend fun buildTemplate(selection: ExportSelection): AppResult<PartyTemplate> = either {
        val partyStart = requirePartyStartDate().bind()
        val tzAt = timeZoneResolver().bind()

        val compos = app.compos.getAllCompos().bind().filter { selection.compoIds.contains(it.id) }
        val compoIndexById = compos.withIndex().associate { (index, compo) -> compo.id to index }

        val events = app.events.getAll().bind().filter { selection.eventIds.contains(it.id) }
        val templateEvents = events.map { event ->
            val actions = app.triggers.getTriggersForSignal(event.signal()).bind()
                .filter { it.enabled }
                .map { it.getAction().bind() }
            TemplateEvent(
                name = event.name,
                start = RelativeTime.fromInstant(event.startTime, partyStart, tzAt),
                end = event.endTime?.let { RelativeTime.fromInstant(it, partyStart, tzAt) },
                visible = event.visible,
                // Triggers pointing at compos left out of the export are dropped.
                triggers = actions.mapNotNull { it.toTemplateTrigger(compoIndexById) },
            )
        }

        PartyTemplate(
            partyboiTemplate = PARTY_TEMPLATE_VERSION,
            exportedAt = Clock.System.now().toString(),
            instanceName = app.config.instanceName,
            generalRules = if (selection.generalRules) app.compos.generalRules.get().bind().rules else null,
            partyDays = if (selection.partyDays) app.settings.partyDays.get().bind() else null,
            timeZone = if (selection.timeZone) app.time.timeZone.get().bind().id else null,
            resultsFileHeader = if (selection.resultsFileHeader) app.settings.resultsFileHeader.get().bind() else null,
            compos = compos.map { TemplateCompo.fromCompo(it) },
            events = templateEvents,
            slideSets = exportableSlideSets().bind()
                .filter { (set, _) -> selection.slideSetIds.contains(set.id) }
                .map { (set, slides) ->
                    TemplateSlideSet(
                        id = set.id,
                        name = set.name,
                        icon = set.icon,
                        maxImageSlides = set.maxImageSlides,
                        slides = slides,
                    )
                },
        )
    }

    // Slide sets with the slides a template can carry (text and QR code, not
    // generated). The ad hoc set is runtime state and never exported.
    suspend fun exportableSlideSets(): AppResult<List<Pair<SlideSetRow, List<TemplateSlide>>>> = either {
        app.screen.getSlideSets().bind()
            .filter { it.id != SlideSetRow.ADHOC }
            .map { set ->
                val slides = app.screen.getSlideSet(set.id).bind()
                    .filter { !it.readOnly }
                    .mapNotNull { row -> TemplateSlide.fromSlide(row.getSlide(), row.visible) }
                set to slides
            }
    }

    suspend fun analyze(template: PartyTemplate): AppResult<ImportPreview> = either {
        val partyStart = requirePartyStartDate().bind()
        // Preview event times with the template's timezone (checked by default on
        // the selection page), falling back to the instance's own. The same zone is
        // carried in the preview so the page displays times as they were computed.
        val templateTz = templateTimeZone(template).bind()
        val previewTz = templateTz ?: app.time.timeZone.get().bind()
        val tzAt = templateTz?.let { tz -> { _: LocalDate -> tz } } ?: timeZoneResolver().bind()
        val existingCompoNames = app.compos.getAllCompos().bind().map { it.name.normalized() }.toSet()
        val existingEventNames = app.events.getAll().bind().map { it.name.normalized() }.toSet()

        ImportPreview(
            previewTimeZone = previewTz,
            hasGeneralRules = template.generalRules != null,
            generalRulesOverwrite = app.compos.generalRules.get().bind().rules.isNotBlank(),
            partyDays = template.partyDays,
            currentPartyDays = app.settings.partyDays.get().bind(),
            timeZone = template.timeZone,
            currentTimeZone = app.time.timeZone.get().bind().id,
            resultsFileHeader = template.resultsFileHeader,
            resultsFileHeaderOverwrite = app.settings.resultsFileHeader.get().bind().isNotBlank(),
            compos = template.compos.mapIndexed { index, compo ->
                PreviewItem(index, compo.name, existingCompoNames.contains(compo.name.normalized()))
            },
            events = template.events.mapIndexed { index, event ->
                PreviewEvent(
                    index = index,
                    name = event.name,
                    startTime = event.start.toInstant(partyStart, tzAt),
                    endTime = event.end?.toInstant(partyStart, tzAt),
                    alreadyExists = existingEventNames.contains(event.name.normalized()),
                    triggerLabels = event.triggers.map { it.label(template.compos) },
                )
            },
            slideSets = template.slideSets.mapIndexed { index, set ->
                val existingNames = existingSlideNames(set.id).bind()
                PreviewSlideSet(
                    index = index,
                    name = set.name,
                    slideCount = set.slides.size,
                    existingCount = set.slides.count { existingNames.contains(it.slideName().normalized()) },
                )
            },
        )
    }

    // Names of the slides already in a slide set; empty when the set does not exist.
    private suspend fun existingSlideNames(slideSetId: String): AppResult<Set<String>> =
        app.screen.getSlideSet(slideSetId).map { rows ->
            rows.map { it.getSlide().getName().normalized() }.toSet()
        }

    suspend fun import(template: PartyTemplate, selection: ImportSelection): AppResult<ImportReport> = either {
        val partyStart = requirePartyStartDate().bind()
        // When the template's timezone is imported, events are wall-clock in that
        // zone; otherwise they land in the instance's current timezone.
        val importedTz = if (selection.timeZone) templateTimeZone(template).bind() else null
        val tzAt = importedTz?.let { tz -> { _: LocalDate -> tz } } ?: timeZoneResolver().bind()
        val existingCompos = app.compos.getAllCompos().bind()
        val existingEvents = app.events.getAll().bind()
        val existingSlideSets = app.screen.getSlideSets().bind().map { it.id }.toSet()
        val existingSlideNamesBySet = template.slideSets.associate { set ->
            set.id to existingSlideNames(set.id).bind()
        }

        val report = app.db.transaction {
            either {
                val createdCompos = mutableListOf<String>()
                val skippedCompos = mutableListOf<String>()
                val compoIdByIndex = mutableMapOf<Int, UUID>()
                // Includes rows created during this import, so two same-named compos
                // inside one template cannot both be created. First match wins.
                val compoIdByName = mutableMapOf<String, UUID>()
                existingCompos.forEach { compoIdByName.putIfAbsent(it.name.normalized(), it.id) }

                template.compos.forEachIndexed { index, templateCompo ->
                    val name = templateCompo.name.normalized()
                    val resolved = compoIdByName[name]
                    when {
                        resolved != null -> {
                            // Also resolved for unselected compos, so triggers can rewire
                            // to them. Duplicates are reported regardless of selection:
                            // already-existing items render as disabled checkboxes, which
                            // browsers never submit.
                            compoIdByIndex[index] = resolved
                            skippedCompos.add(templateCompo.name)
                        }

                        selection.compos.contains(index) -> {
                            val created = app.compos.create(templateCompo.toCompo(), this@transaction).bind()
                            compoIdByIndex[index] = created.id
                            compoIdByName[name] = created.id
                            createdCompos.add(created.name)
                        }
                    }
                }

                val createdEvents = mutableListOf<String>()
                val skippedEvents = mutableListOf<String>()
                val droppedTriggers = mutableListOf<String>()
                var createdTriggers = 0
                val eventNames = existingEvents.map { it.name.normalized() }.toMutableSet()

                template.events.forEachIndexed { index, templateEvent ->
                    if (eventNames.contains(templateEvent.name.normalized())) {
                        skippedEvents.add(templateEvent.name)
                        return@forEachIndexed
                    }
                    if (!selection.events.contains(index)) return@forEachIndexed
                    eventNames.add(templateEvent.name.normalized())
                    val event = app.events.add(
                        NewEvent(
                            name = templateEvent.name,
                            startTime = templateEvent.start.toInstant(partyStart, tzAt),
                            endTime = templateEvent.end?.toInstant(partyStart, tzAt),
                            visible = templateEvent.visible,
                        ),
                        this@transaction,
                    ).bind()
                    createdEvents.add(event.name)
                    templateEvent.triggers.forEach { trigger ->
                        val action = trigger.toAction(compoIdByIndex)
                        if (action == null) {
                            droppedTriggers.add("${event.name}: ${trigger.label(template.compos)}")
                        } else {
                            app.triggers.add(event.signal(), action, this@transaction).bind()
                            createdTriggers++
                        }
                    }
                }

                val createdSlides = mutableListOf<String>()
                val skippedSlides = mutableListOf<String>()

                template.slideSets.forEachIndexed { index, templateSet ->
                    if (templateSet.id == SlideSetRow.ADHOC) return@forEachIndexed
                    val selected = selection.slideSets.contains(index)
                    if (selected && !existingSlideSets.contains(templateSet.id)) {
                        app.screen.upsertSlideSet(
                            templateSet.id, templateSet.name, templateSet.icon, this@transaction
                        ).bind()
                        templateSet.maxImageSlides?.let {
                            app.screen.setMaxImageSlides(templateSet.id, it, this@transaction).bind()
                        }
                    }
                    // Same-named slides are reported as skipped even when the set is
                    // unselected (its checkbox may be disabled, and disabled controls
                    // are never submitted); names seen during this import count too.
                    val seenNames = (existingSlideNamesBySet[templateSet.id] ?: emptySet()).toMutableSet()
                    templateSet.slides.forEach { slide ->
                        val name = slide.slideName().normalized()
                        when {
                            seenNames.contains(name) ->
                                skippedSlides.add("${templateSet.name}: ${slide.slideName()}")

                            selected -> {
                                seenNames.add(name)
                                app.screen.addSlide(
                                    templateSet.id, slide.toSlide(), slide.visible, this@transaction
                                ).bind()
                                createdSlides.add("${templateSet.name}: ${slide.slideName()}")
                            }
                        }
                    }
                }

                ImportReport(
                    createdCompos = createdCompos,
                    skippedCompos = skippedCompos,
                    createdEvents = createdEvents,
                    skippedEvents = skippedEvents,
                    createdTriggers = createdTriggers,
                    droppedTriggers = droppedTriggers,
                    createdSlides = createdSlides,
                    skippedSlides = skippedSlides,
                    generalRulesImported = false,
                    importedSettings = emptyList(),
                )
            }
        }.bind()

        // The property store cannot join the transaction; writing after the commit
        // means a rollback never leaves the rules or settings half-imported.
        val importRules = selection.generalRules && template.generalRules != null
        if (importRules) {
            app.compos.generalRules.set(GeneralRules(template.generalRules!!)).bind()
        }
        val importedSettings = mutableListOf<String>()
        if (selection.partyDays && template.partyDays != null) {
            app.settings.partyDays.set(template.partyDays).bind()
            importedSettings.add("Party length: ${template.partyDays} days")
        }
        if (importedTz != null) {
            app.time.timeZone.set(importedTz).bind()
            importedSettings.add("Time zone: ${importedTz.id}")
        }
        if (selection.resultsFileHeader && template.resultsFileHeader != null) {
            app.settings.resultsFileHeader.set(template.resultsFileHeader).bind()
            importedSettings.add("results.txt header")
        }
        report.copy(generalRulesImported = importRules, importedSettings = importedSettings)
    }

    private suspend fun requirePartyStartDate(): AppResult<LocalDate> = either {
        app.settings.partyStartDate.get().bind()
            ?: raise(ValidationError("partyStartDate", "Set the party start date in settings first", ""))
    }

    // The timezone can vary by date (overrides), so hand out a pure per-date resolver.
    private suspend fun timeZoneResolver(): AppResult<(LocalDate) -> TimeZone> = either {
        val overrides = app.time.timeZoneOverrides.get().bind()
        val base = app.time.timeZone.get().bind()
        ({ date: LocalDate -> overrides[date] ?: base })
    }

    private fun templateTimeZone(template: PartyTemplate): AppResult<TimeZone?> = either {
        template.timeZone?.let {
            try {
                TimeZone.of(it)
            } catch (e: Exception) {
                raise(ValidationError("file", "Unknown time zone in template: $it", it))
            }
        }
    }
}

private fun String.normalized() = trim().lowercase()

data class ExportSelection(
    @Custom
    val generalRules: Boolean,
    @Custom
    val partyDays: Boolean,
    @Custom
    val timeZone: Boolean,
    @Custom
    val resultsFileHeader: Boolean,
    @Custom
    val compoIds: List<UUID>,
    @Custom
    val eventIds: List<UUID>,
    @Custom
    val slideSetIds: List<String>,
) : Validateable<ExportSelection>

data class TemplateUpload(
    @Field(label = "Template file", description = "A party template JSON file exported from Partyboi")
    val file: FileUpload,
) : Validateable<TemplateUpload> {
    override fun validationErrors() = listOf(
        cond("file", file.name, !file.isDefined, "Select a template file")
    )

    companion object {
        val Empty = TemplateUpload(FileUpload.Empty)
    }
}

data class ImportSelection(
    @Hidden
    val payload: String,
    @Custom
    val generalRules: Boolean,
    @Custom
    val partyDays: Boolean,
    @Custom
    val timeZone: Boolean,
    @Custom
    val resultsFileHeader: Boolean,
    @Custom
    val compos: List<Int>,
    @Custom
    val events: List<Int>,
    @Custom
    val slideSets: List<Int>,
) : Validateable<ImportSelection>

data class ImportPreview(
    // The zone event preview times were computed in — the page must display them
    // in this zone, or the admin approves times that don't match the result.
    val previewTimeZone: TimeZone,
    val hasGeneralRules: Boolean,
    val generalRulesOverwrite: Boolean,
    val partyDays: Int?,
    val currentPartyDays: Int,
    val timeZone: String?,
    val currentTimeZone: String,
    val resultsFileHeader: String?,
    val resultsFileHeaderOverwrite: Boolean,
    val compos: List<PreviewItem>,
    val events: List<PreviewEvent>,
    val slideSets: List<PreviewSlideSet>,
) {
    val hasSettings = partyDays != null || timeZone != null || resultsFileHeader != null
    val hasNothing =
        !hasGeneralRules && !hasSettings && compos.isEmpty() && events.isEmpty() && slideSets.isEmpty()
}

data class PreviewItem(
    val index: Int,
    val name: String,
    val alreadyExists: Boolean,
)

data class PreviewEvent(
    val index: Int,
    val name: String,
    val startTime: Instant,
    val endTime: Instant?,
    val alreadyExists: Boolean,
    val triggerLabels: List<String>,
)

data class PreviewSlideSet(
    val index: Int,
    val name: String,
    val slideCount: Int,
    val existingCount: Int,
) {
    val alreadyExists = slideCount > 0 && existingCount == slideCount
}

data class ImportReport(
    val createdCompos: List<String>,
    val skippedCompos: List<String>,
    val createdEvents: List<String>,
    val skippedEvents: List<String>,
    val createdTriggers: Int,
    val droppedTriggers: List<String>,
    val createdSlides: List<String>,
    val skippedSlides: List<String>,
    val generalRulesImported: Boolean,
    val importedSettings: List<String>,
)
