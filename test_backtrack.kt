fun main() {
    val text = "Hello \u00ADworld-this is a test"
    var safeNextOffset = 7 // After "\u00AD"
    val before = text[safeNextOffset - 1]
    println("before is \\u00AD: ${before == '\u00AD'}")
}
