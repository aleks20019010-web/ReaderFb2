sed -i -e '/updatePreferences()/a\                val cutoutPx = (activity as? BookReaderActivity)?.systemCutoutTop ?: 0\
                updateTopMargin(cutoutPx)' app/src/main/java/com/nightread/app/ui/BookReaderFragment.kt
