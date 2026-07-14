fun main() {
    val v = "[аеёиоуыэюяАЕЁИОУЫЭЮЯ]"
    val c = "[бвгджзклмнпрстфхцчшщБВГДЖЗКЛМНПРСТФХЦЧШЩ]"
    val s = "[йьъЙЬЪ]"

    val regex1 = Regex("($v)(?=$c$v)")
    val regex2 = Regex("($v$c)(?=$c$v)")
    val regex3 = Regex("($v$c)(?=$c$c$v)")
    val regex4 = Regex("($v$s)(?=$c$v)")

    var text = "велосипед поддержать стройка подъезд"
    text = regex1.replace(text, "$1\u00AD")
    text = regex2.replace(text, "$1\u00AD")
    text = regex3.replace(text, "$1\u00AD")
    text = regex4.replace(text, "$1\u00AD")
    
    // Check constraints: no hyphen at beginning/end of words
    // Actually, if we just do these replacements, do they put hyphen at beginning?
    // They look for v followed by cv, so minimum characters before hyphen is 1 (v).
    // And after hyphen is 2 (cv). So min word length is 3. 
    // Wait, regex1 is v(?=cv). So length is 3. The hyphen is after v. So it's not at the beginning!
    // And it's not at the end, because cv is after it.
    
    println(text.replace("\u00AD", "-"))
}
