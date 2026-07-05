sed -i 's/import kotlinx.coroutines.CancellationException//g' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
sed -i 's/import kotlinx.coroutines.isActive//g' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
sed -i 's/^import android.content.Context/import android.content.Context\nimport kotlinx.coroutines.isActive\nimport kotlinx.coroutines.CancellationException/' app/src/main/java/com/nightread/app/service/NewBookScanner.kt
