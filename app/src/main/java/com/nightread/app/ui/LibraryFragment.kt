package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nightread.app.R
import com.nightread.app.data.BookEntity

class LibraryFragment : Fragment() {

    private val viewModel: BookViewModel by activityViewModels()

    private val manualImportLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            val ctx = context ?: return@registerForActivityResult
            viewModel.importBooksFromUris(uris, ctx) { successCount, totalCount ->
                activity?.runOnUiThread {
                    if (successCount == totalCount) {
                        CustomToast.show(ctx, "Успешно добавлено книг: $successCount")
                    } else if (successCount > 0) {
                        CustomToast.show(ctx, "Добавлено книг: $successCount из $totalCount")
                    } else {
                        CustomToast.show(ctx, "Ошибка при импорте выбранных файлов")
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(filter: String): LibraryFragment {
            val fragment = LibraryFragment()
            val args = Bundle()
            args.putString("FILTER_TYPE", filter)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.library_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val composeView = view.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.composeLibraryView)
        composeView?.setContent {
            com.nightread.app.ui.theme.MyApplicationTheme {
                val searchedBooks by viewModel.searchedBooks.collectAsState(initial = emptyList())
                val sortOption by viewModel.sortOption.collectAsState(initial = com.nightread.app.data.SettingsManager.SORT_DATE_DESC)
                val sortedBooks = remember(searchedBooks, sortOption) { viewModel.sortBooks(searchedBooks.distinctBy { it.sha1 }, sortOption) }

                val scanState by viewModel.scanState.collectAsState()
                val isScanning = scanState.isScanning
                val scanProgressText = scanState.status

                var isGridView by remember { androidx.compose.runtime.mutableStateOf(true) }
                var isSearchActive by remember { androidx.compose.runtime.mutableStateOf(false) }
                var searchQuery by remember { androidx.compose.runtime.mutableStateOf("") }
                var showSortDialog by remember { androidx.compose.runtime.mutableStateOf(false) }

                LibraryComposeUI(
                    books = sortedBooks,
                    isScanning = isScanning,
                    scanProgressText = scanProgressText,
                    isGridView = isGridView,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { query ->
                        searchQuery = query
                        viewModel.setSearchQuery(query)
                    },
                    onSearchActiveChanged = { active ->
                        isSearchActive = active
                        if (!active) {
                            searchQuery = ""
                            viewModel.setSearchQuery("")
                        }
                    },
                    onScanClicked = { checkStoragePermissionAndScan() },
                    onSearchClicked = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            viewModel.setSearchQuery("")
                        }
                    },
                    onViewModeClicked = {
                        isGridView = !isGridView
                        CustomToast.show(requireContext(), if (isGridView) "Вид: Сетка" else "Вид: Список")
                    },
                    onManualImportClicked = {
                        try {
                            manualImportLauncher.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "text/xml", "application/zip", "application/x-mobipocket-ebook", "*/*"))
                        } catch (e: Exception) {
                            CustomToast.show(requireContext(), "Не удалось открыть выбор файлов: ${e.message}")
                        }
                    },
                    onSortClicked = {
                        showSortDialog = true
                    },
                    onCancelScanClicked = {
                        viewModel.cancelAllScanningTasks()
                        CustomToast.show(requireContext(), "Сканирование остановлено")
                    },
                    onMenuClicked = {
                        (requireActivity() as? com.nightread.app.MainActivity)?.openDrawer()
                    },
                    onBookClicked = { book ->
                        com.nightread.app.data.BookPreloader.preload(requireContext(), book.sha1, book.filePath)
                        viewModel.openBook(book)
                        val intent = android.content.Intent(requireContext(), BookDetailActivity::class.java).apply {
                            putExtra("BOOK_SHA1", book.sha1)
                        }
                        startActivity(intent)
                    }
                )

                if (showSortDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showSortDialog = false },
                        title = { androidx.compose.material3.Text("Сортировка книг", color = Color(0xFFF1F5F9)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val options = listOf(
                                    com.nightread.app.data.SettingsManager.SORT_DATE_DESC to "Сначала новые",
                                    com.nightread.app.data.SettingsManager.SORT_DATE_ASC to "Сначала старые",
                                    com.nightread.app.data.SettingsManager.SORT_TITLE_ASC to "По названию (А–Я)",
                                    com.nightread.app.data.SettingsManager.SORT_TITLE_DESC to "По названию (Я–А)",
                                    com.nightread.app.data.SettingsManager.SORT_AUTHOR_ASC to "По автору (А–Я)",
                                    com.nightread.app.data.SettingsManager.SORT_AUTHOR_DESC to "По автору (Я–А)",
                                    com.nightread.app.data.SettingsManager.SORT_PROGRESS_DESC to "По прогрессу чтения"
                                )
                                options.forEach { (optionKey, optionLabel) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.setSortOption(optionKey)
                                                showSortDialog = false
                                                CustomToast.show(requireContext(), "Сортировка: $optionLabel")
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.RadioButton(
                                            selected = sortOption == optionKey,
                                            onClick = {
                                                viewModel.setSortOption(optionKey)
                                                showSortDialog = false
                                                CustomToast.show(requireContext(), "Сортировка: $optionLabel")
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        androidx.compose.material3.Text(
                                            text = optionLabel,
                                            color = Color(0xFFF1F5F9),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { showSortDialog = false }) {
                                androidx.compose.material3.Text("Закрыть", color = Color(0xFF00BCD4))
                            }
                        },
                        containerColor = Color(0xFF192236),
                        titleContentColor = Color(0xFFF1F5F9),
                        textContentColor = Color(0xFFF1F5F9)
                    )
                }
            }
        }
    }

    private fun checkStoragePermissionAndScan() {
        val act = activity ?: return
        val ctx = context ?: return
        com.nightread.app.util.StoragePermissionHelper.checkAndRequestStoragePermission(act) {
            viewModel.startLocalBookScan()
            CustomToast.show(ctx, "Запуск сканирования книг...")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ctx = context ?: return
        if (com.nightread.app.util.StoragePermissionHelper.hasStoragePermission(ctx)) {
            viewModel.startLocalBookScan()
            CustomToast.show(ctx, "Запуск сканирования книг...")
        } else {
            CustomToast.show(ctx, getString(R.string.storage_permission_denied))
        }
    }
}
