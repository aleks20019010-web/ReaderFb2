# Remove touchOverlay
sed -i 's/private lateinit var touchOverlay: View//' app/src/main/java/com/nightread/app/ui/ReadingActivity.kt
sed -i 's/touchOverlay = findViewById(R.id.touchOverlay)//' app/src/main/java/com/nightread/app/ui/ReadingActivity.kt
