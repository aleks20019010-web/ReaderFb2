sed -i -e '/action = com.nightread.app.service.TtsForegroundService.ACTION_START/a\                        val startIdx = viewModel.bookState.value?.currentProgressChar ?: 0\
                        putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_START_IDX, startIdx)' app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt
