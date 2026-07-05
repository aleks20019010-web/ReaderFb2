sed -i 's/fun splitText(/suspend fun splitText(/g' app/src/main/java/com/nightread/app/ui/PageSplitter.kt
sed -i 's/import android.util.Log/import android.util.Log\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\nimport kotlinx.coroutines.isActive/' app/src/main/java/com/nightread/app/ui/PageSplitter.kt

# Wrap the body in withContext(Dispatchers.Default) { ... }
sed -i 's/fun splitText(/fun splitText(/g' app/src/main/java/com/nightread/app/ui/PageSplitter.kt # dummy
