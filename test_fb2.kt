import java.util.regex.Regex

fun main() {
    var text = """
        <p>Paragraph 1</p>
        <empty-line/>
        <empty-line/>
        <p>Paragraph 2</p>
        <p>- Dialog 1</p>
        <p> - Dialog 2</p>
    """

    text = text.replace(Regex("<empty-line[^>]*>"), "\n\n")
    text = text.replace(Regex("<p[^>]*>"), "\n\u00A0\u00A0\u00A0\u00A0")
    text = text.replace(Regex("</p>"), "")
    text = text.replace(Regex("<[^>]+>"), "")

    println("After tags removed:")
    println(text.replace("\u00A0", "X"))

    text = text.replace(Regex("([ \\t\\r]*\\n[ \\t\\r]*){2,}"), "\n")
    text = text.replace(Regex("\\n[ \\t\\r]+(?=\\u00A0)"), "\n")

    println("\nFinal text:")
    println(text.replace("\u00A0", "X"))
}
