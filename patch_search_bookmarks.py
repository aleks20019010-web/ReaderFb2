import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

# 1. Imports
imports = """
import com.nightread.app.data.BookmarkDatabase
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.BookmarkRepository
import com.nightread.app.ui.customlayout.ReaderSearchEngine
import com.nightread.app.ui.customlayout.ReaderSearchResult
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
"""
content = content.replace("import androidx.compose.material.icons.filled.Search\n", imports)

# 2. Add State inside ReaderComposeScreen
states = """
    var isSettingsOpen by remember { mutableStateOf(false) }
    
    // --- Bookmarks & Search State ---
    val bookmarkDb = remember(context) { BookmarkDatabase.getDatabase(context) }
    val bookmarkRepo = remember(bookmarkDb) { BookmarkRepository(bookmarkDb.bookmarkDao()) }
    val bookmarks by bookmarkRepo.getBookmarksForBook(sha1).collectAsState(initial = emptyList())
    var showBookmarksSheet by remember { mutableStateOf(false) }
    
    val searchEngine = remember(mainText) { ReaderSearchEngine(mainText) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ReaderSearchResult>>(emptyList()) }
    var currentSearchIndex by remember { mutableIntStateOf(-1) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    
    var pendingTargetOffset by remember { mutableStateOf<Int?>(null) }
    // --------------------------------
"""
content = content.replace("    var isSettingsOpen by remember { mutableStateOf(false) }", states)

# 3. Add to ReaderLayoutEngine.createPager
create_pager_replace = """
                                val pager = com.nightread.app.ui.customlayout.ReaderLayoutEngine.createPager(
                                    context = context,
                                    document = doc,
                                    config = config,
                                    viewport = viewport,
                                    textMeasurer = textMeasurer,
                                    scope = this,
                                    initialTargetOffset = savedTextOffset
                                )
                                
                                launch {
                                    androidx.compose.runtime.snapshotFlow { pendingTargetOffset }.collect { target ->
                                        if (target != null) {
                                            pager.goToOffset(target)
                                        }
                                    }
                                }
"""
content = content.replace("""                                val pager = com.nightread.app.ui.customlayout.ReaderLayoutEngine.createPager(
                                    context = context,
                                    document = doc,
                                    config = config,
                                    viewport = viewport,
                                    textMeasurer = textMeasurer,
                                    scope = this,
                                    initialTargetOffset = savedTextOffset
                                )""", create_pager_replace)

# 4. Handle Pending Target Offset
pending_offset_logic = """
                                        if (isRestoringProgress && currentOffsets.isNotEmpty()) {
                                            val targetPage = findPageForOffset(currentOffsets, savedTextOffset)
                                            pagerState.scrollToPage(targetPage)
                                            isRestoringProgress = false
                                        }
                                        if (pendingTargetOffset != null && updatedPages.isNotEmpty()) {
                                            val target = pendingTargetOffset!!
                                            var targetPage = -1
                                            for (i in updatedPages.indices) {
                                                val p = updatedPages[i]
                                                if (target >= p.startOffset && target < p.endOffset) {
                                                    targetPage = i
                                                    break
                                                }
                                            }
                                            if (targetPage == -1 && updatedPages.isNotEmpty() && target == updatedPages.last().endOffset) {
                                                targetPage = updatedPages.size - 1
                                            }
                                            
                                            if (targetPage != -1) {
                                                pagerState.scrollToPage(targetPage)
                                                pendingTargetOffset = null
                                            }
                                        }
"""
content = content.replace("""                                        if (isRestoringProgress && currentOffsets.isNotEmpty()) {
                                            val targetPage = findPageForOffset(currentOffsets, savedTextOffset)
                                            pagerState.scrollToPage(targetPage)
                                            isRestoringProgress = false
                                        }""", pending_offset_logic)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
