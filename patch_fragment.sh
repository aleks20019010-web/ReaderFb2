sed -i -e '/val resourceId = context.resources.getIdentifier/i\            val cutoutPx = (activity as? BookReaderActivity)?.systemCutoutTop ?: 0\
            val cutoutDp = (cutoutPx / density).toInt()\
            val topMarginDp = cutoutDp + 3' app/src/main/java/com/nightread/app/ui/BookReaderFragment.kt
