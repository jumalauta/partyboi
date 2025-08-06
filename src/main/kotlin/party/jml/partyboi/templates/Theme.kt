package party.jml.partyboi.templates

data class Theme(
    val colorScheme: ColorScheme,
) {
    companion object {
        val Default = Theme(
            colorScheme = ColorScheme.Blue,
        )
    }
}

enum class ColorScheme(val displayName: String, val filename: String) {
    Red("Red", "pico.red.min.css"),
    Pink("Pink", "pico.pink.min.css"),
    Fuchsia("Fuchsia", "pico.fuchsia.min.css"),
    Purple("Purple", "pico.purple.min.css"),
    Violet("Violet", "pico.violet.min.css"),
    Indigo("Indigo", "pico.indigo.min.css"),
    Blue("Blue", "pico.min.css"),
    Azure("Azure", "pico.azure.min.css"),
    Cyan("Cyan", "pico.cyan.min.css"),
    Jade("Jade", "pico.jade.min.css"),
    Green("Green", "pico.green.min.css"),
    Lime("Lime", "pico.lime.min.css"),
    Yellow("Yellow", "pico.yellow.min.css"),
    Amber("Amber", "pico.amber.min.css"),
    Pumpkin("Pumpkin", "pico.pumpkin.min.css"),
    Orange("Orange", "pico.orange.min.css"),
    Sand("Sand", "pico.sand.min.css"),
    Grey("Grey", "pico.grey.min.css"),
    Zinc("Zinc", "pico.zinc.min.css"),
    Slate("Slate", "pico.slate.min.css")
}