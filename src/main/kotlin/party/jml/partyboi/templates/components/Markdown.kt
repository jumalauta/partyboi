package party.jml.partyboi.templates.components

import kotlinx.html.*
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

fun FlowContent.markdown(source: String) {
    div {
        unsafe {
            val fixedSource = source.replace("\r\n", "\n").trim()
            val flavour = CommonMarkFlavourDescriptor()
            val parser = MarkdownParser(flavour)
            val ast = parser.buildMarkdownTreeFromString(fixedSource)
            val html = HtmlGenerator(fixedSource, ast, flavour).generateHtml()
            val safelist = Safelist.relaxed()
                .addAttributes("a", "target")
                .removeProtocols("a", "href", "ftp", "javascript")
            raw(Jsoup.clean(html, safelist))
        }
    }
}