sed -i '/val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(/,/) {/d' app/src/main/java/com/nightread/app/ui/LibraryFragment.kt
sed -i 's/startActivity(intent, options.toBundle())/startActivity(intent)/g' app/src/main/java/com/nightread/app/ui/LibraryFragment.kt
