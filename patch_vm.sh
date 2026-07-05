sed -i 's/viewModelScope.launch {/viewModelScope.launch {\n            if (!isActive) return@launch/g' app/src/main/java/com/nightread/app/ui/BookViewModel.kt
sed -i 's/viewModelScope.launch(Dispatchers.IO) {/viewModelScope.launch(Dispatchers.IO) {\n            if (!isActive) return@launch/g' app/src/main/java/com/nightread/app/ui/BookViewModel.kt
sed -i 's/viewModelScope.launch(Dispatchers.Main) {/viewModelScope.launch(Dispatchers.Main) {\n            if (!isActive) return@launch/g' app/src/main/java/com/nightread/app/ui/BookViewModel.kt
sed -i '13a\
import kotlinx.coroutines.isActive\
import kotlinx.coroutines.CancellationException\
' app/src/main/java/com/nightread/app/ui/BookViewModel.kt
