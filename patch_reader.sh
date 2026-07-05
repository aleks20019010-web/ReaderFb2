sed -i '34a\
import kotlinx.coroutines.isActive\
import kotlinx.coroutines.CancellationException\
' app/src/main/java/com/nightread/app/ui/ReaderActivity.kt

# In loadBookFromDbAndRead
sed -i 's/val db = AppDatabase.getDatabase(this@ReaderActivity)/if (!isActive || isFinishing || isDestroyed) return@launch\n                val db = AppDatabase.getDatabase(this@ReaderActivity)/' app/src/main/java/com/nightread/app/ui/ReaderActivity.kt

sed -i 's/} catch (t: Throwable) {/} catch (e: CancellationException) {\n                throw e\n            } catch (t: Throwable) {/g' app/src/main/java/com/nightread/app/ui/ReaderActivity.kt

sed -i 's/val file = File(path)/if (!isActive || isFinishing || isDestroyed) return@launch\n                val file = File(path)/' app/src/main/java/com/nightread/app/ui/ReaderActivity.kt

sed -i 's/val vpWidth = viewPager.width/if (!isActive) return\n        val vpWidth = viewPager.width/' app/src/main/java/com/nightread/app/ui/ReaderActivity.kt

