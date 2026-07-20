import java.io.File
fun main() {
    val content = File("test.fb2").readText()
    val start = System.currentTimeMillis()
    val tagRegex = Regex("<(p|title|subtitle|h1|h2|h3|h4|h5|h6)(\\s+[^>]*|\\s*)>", RegexOption.IGNORE_CASE)
    val offsets = tagRegex.findAll(content).map { it.range.first }.toList()
    val end = System.currentTimeMillis()
    println("Regex took ${end - start} ms, found ${offsets.size}")
}
