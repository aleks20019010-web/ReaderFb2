sed -i -e '/val lux = lastKnownLux/,/}/c\
            val targetTheme = if (com.nightread.app.data.ThemeHelper.isNightTime()) preferredNightTheme else preferredDayTheme' app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt
