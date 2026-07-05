sed -i 's/super.onViewCreated(null,/super.onViewCreated(view,/g' app/src/main/java/com/nightread/app/ui/LibraryFragment.kt
sed -i 's/adapter = BookAdapter(/adapter = BookAdapter(\n            books = emptyList(),/g' app/src/main/java/com/nightread/app/ui/LibraryFragment.kt
