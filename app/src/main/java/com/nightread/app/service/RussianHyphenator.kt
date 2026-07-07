package com.nightread.app.service

object RussianHyphenator {
    private const val SOFT_HYPHEN = "\u00AD"
    
    // Simple regex for VCCV
    private val patternVCCV = Regex("([аеёиоуыэюяАЕЁИОУЫЭЮЯ])([бвгджзйклмнпрстфхцчшщБВГДЖЗЙКЛМНПРСТФХЦЧШЩ])([бвгджзйклмнпрстфхцчшщБВГДЖЗЙКЛМНПРСТФХЦЧШЩ])([аеёиоуыэюяАЕЁИОУЫЭЮЯ])")
    
    // Simple regex for VCV
    private val patternVCV = Regex("([аеёиоуыэюяАЕЁИОУЫЭЮЯ])([бвгджзклмнпрстфхцчшщБВГДЖЗКЛМНПРСТФХЦЧШЩ])([аеёиоуыэюяАЕЁИОУЫЭЮЯ])")
    
    // Simple regex for VCCCV
    private val patternVCCCV = Regex("([аеёиоуыэюяАЕЁИОУЫЭЮЯ])([бвгджзйклмнпрстфхцчшщБВГДЖЗЙКЛМНПРСТФХЦЧШЩ])([бвгджзйклмнпрстфхцчшщБВГДЖЗЙКЛМНПРСТФХЦЧШЩ]{2,})([аеёиоуыэюяАЕЁИОУЫЭЮЯ])")

    fun hyphenate(text: String): String {
        var result = text
        // V C - C V
        result = patternVCCV.replace(result, "$1$2$SOFT_HYPHEN$3$4")
        result = patternVCCV.replace(result, "$1$2$SOFT_HYPHEN$3$4")
        
        // V - C V
        result = patternVCV.replace(result, "$1$SOFT_HYPHEN$2$3")
        result = patternVCV.replace(result, "$1$SOFT_HYPHEN$2$3")
        
        // VC - CCV
        result = patternVCCCV.replace(result, "$1$2$SOFT_HYPHEN$3$4")
        result = patternVCCCV.replace(result, "$1$2$SOFT_HYPHEN$3$4")

        return result
    }
}
