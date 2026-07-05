sed -i '/^import kotlinx.coroutines.CancellationException/d' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
sed -i '/^import kotlinx.coroutines.isActive/d' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
sed -i '2a\
import kotlinx.coroutines.CancellationException\
import kotlinx.coroutines.isActive' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
