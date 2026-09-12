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

// hex is the light-mode --pico-primary of the CSS file the entry serves; it must stay in sync
// with the filename so the logo and favicon match the stylesheet.
enum class ColorScheme(val displayName: String, val filename: String, val hex: String) {
    Red("Red", "pico.red.min.css", "#c52f21"),
    Pink("Pink", "pico.pink.min.css", "#c72259"),
    Fuchsia("Fuchsia", "pico.fuchsia.min.css", "#c1208b"),
    Purple("Purple", "pico.purple.min.css", "#aa40bf"),
    Violet("Violet", "pico.violet.min.css", "#8352c5"),
    Indigo("Indigo", "pico.indigo.min.css", "#655cd6"),

    // Blue is the persisted default and has always pointed to pico.min.css (the azure theme),
    // so its hex is azure's #0172ad — remapping it to pico.blue.min.css would restyle
    // existing installations.
    Blue("Blue", "pico.min.css", "#0172ad"),
    Azure("Azure", "pico.min.css", "#0172ad"),
    Cyan("Cyan", "pico.cyan.min.css", "#047878"),
    Jade("Jade", "pico.jade.min.css", "#007a50"),
    Green("Green", "pico.green.min.css", "#33790f"),
    Lime("Lime", "pico.lime.min.css", "#577400"),
    Yellow("Yellow", "pico.yellow.min.css", "#756b00"),
    Amber("Amber", "pico.amber.min.css", "#876400"),
    Pumpkin("Pumpkin", "pico.pumpkin.min.css", "#9c5900"),
    Orange("Orange", "pico.orange.min.css", "#bd3c13"),
    Sand("Sand", "pico.sand.min.css", "#6e6a60"),
    Grey("Grey", "pico.grey.min.css", "#6a6a6a"),
    Zinc("Zinc", "pico.zinc.min.css", "#646b79"),
    Slate("Slate", "pico.slate.min.css", "#5d6b89")
}