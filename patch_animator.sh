sed -i 's/SeriesGroupAdapter/BookAdapter/g' app/src/main/java/com/nightread/app/ui/HighlightItemAnimator.kt
sed -i 's/private var isGridView: Boolean = true/val newlyAddedSha1s = mutableSetOf<String>()\n    private var isGridView: Boolean = true/g' app/src/main/java/com/nightread/app/ui/BookAdapter.kt
