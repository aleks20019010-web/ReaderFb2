sed -i 's/for (path in paths) {/for (path in paths) {\n            if (!kotlin.coroutines.coroutineContext.isActive) return/' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
sed -i 's/for ((index, file) in filesToProcess.withIndex()) {/for ((index, file) in filesToProcess.withIndex()) {\n            if (!kotlin.coroutines.coroutineContext.isActive) return/' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
sed -i '34a\
import kotlinx.coroutines.isActive\
' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
