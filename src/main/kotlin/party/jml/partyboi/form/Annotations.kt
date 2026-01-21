package party.jml.partyboi.form

annotation class Field(
    val label: String = "",
    val presentation: FieldPresentation = FieldPresentation.normal,
    val description: String = "",
)

annotation class Label(val text: String)
annotation class Presentation(val presentation: FieldPresentation)
annotation class Description(val text: String)
annotation class Hidden()
annotation class Large()
annotation class Custom()
annotation class PropertyName(val name: String)