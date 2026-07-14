fun main() {
    var text = "Hello\n\n\n\nWorld\n \n \nTest\n\t\nEnd"
    text = text.replace(Regex("([ \\t\\r]*\\n[ \\t\\r]*){2,}"), "\n")
    println(text.replace("\n", "\\n"))
}
