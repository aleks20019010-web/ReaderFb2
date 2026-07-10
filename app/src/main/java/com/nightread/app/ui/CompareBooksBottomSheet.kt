package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.data.BookEntity
import com.nightread.app.data.BookRepository
import com.nightread.app.databinding.BottomSheetCompareBooksBinding
import com.nightread.app.service.LocalAIManager
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import com.nightread.app.service.Fb2Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompareBooksBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCompareBooksBinding? = null
    private val binding get() = _binding!!

    private var book1: BookEntity? = null
    private var book2: BookEntity? = null

    private lateinit var bookRepository: BookRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCompareBooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        bookRepository = BookRepository(db.bookDao(), db.noteDao())

        binding.btnSelectBook1.setOnClickListener { showBookPicker(1) }
        binding.btnSelectBook2.setOnClickListener { showBookPicker(2) }
        binding.btnCompare.setOnClickListener { startComparison() }
    }

    private fun showBookPicker(index: Int) {
        lifecycleScope.launch {
            val books = bookRepository.allBooks.first()
            if (books.isEmpty()) {
                AiToast.show(requireContext(), "Библиотека пуста. Добавьте книги для сравнения.", ToastType.WARNING)
                return@launch
            }

            val context = requireContext()
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_book_picker, null)
            val etSearch = dialogView.findViewById<EditText>(R.id.etSearch)
            val rvBooks = dialogView.findViewById<RecyclerView>(R.id.rvBooks)

            var dialog: AlertDialog? = null

            val adapter = BookPickerAdapter(books) { selectedBook ->
                if (index == 1) {
                    book1 = selectedBook
                    binding.tvTitle1.text = selectedBook.title
                } else {
                    book2 = selectedBook
                    binding.tvTitle2.text = selectedBook.title
                }
                checkComparisonReady()
                dialog?.dismiss()
            }

            rvBooks.layoutManager = LinearLayoutManager(context)
            rvBooks.adapter = adapter

            dialog = AlertDialog.Builder(context, R.style.Theme_NightRead_Dialog)
                .setView(dialogView)
                .create()

            etSearch.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    adapter.filter(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            dialog.show()
        }
    }

    private fun checkComparisonReady() {
        binding.btnCompare.isEnabled = book1 != null && book2 != null
    }

    private suspend fun getBookContentSnippet(book: BookEntity): String = withContext(Dispatchers.IO) {
        val cached = BookCache.getCachedText(book.sha1)
        if (cached != null) return@withContext cached.take(15000)

        val path = book.filePath ?: return@withContext ""
        val file = File(path)
        if (!file.exists()) return@withContext ""
        try {
            val ext = file.extension.lowercase(Locale.ROOT)
            val rawText = when (ext) {
                "fb2", "xml" -> {
                    val bytes = file.readBytes()
                    val xmlContent = decodeFb2Bytes(bytes)
                    Fb2Parser.extractText(xmlContent)
                }
                "zip" -> {
                    var content = ""
                    FileInputStream(file).use { fis ->
                        ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && (entry.name.endsWith(".fb2") || entry.name.endsWith(".xml"))) {
                                    val bytes = zis.readBytes()
                                    val xmlContent = decodeFb2Bytes(bytes)
                                    content = Fb2Parser.extractText(xmlContent)
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                    content
                }
                "txt" -> file.readText(StandardCharsets.UTF_8)
                else -> ""
            }
            rawText.take(15000)
        } catch (e: Exception) {
            ""
        }
    }

    private fun decodeFb2Bytes(bytes: ByteArray): String {
        val header = String(bytes, 0, minOf(bytes.size, 1024), StandardCharsets.ISO_8859_1)
        val match = Regex("""<\?xml[^>]*encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(header)
        val charsetName = match?.groupValues?.get(1)?.trim() ?: "UTF-8"
        return try {
            String(bytes, java.nio.charset.Charset.forName(charsetName))
        } catch (e: Exception) {
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun startComparison() {
        val b1 = book1 ?: return
        val b2 = book2 ?: return

        if (!LocalAIManager.isModelLoaded) {
            AiToast.show(requireContext(), "Загрузите модель AI в настройках", ToastType.WARNING)
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCompare.isEnabled = false
        binding.scrollViewResult.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Get snippets for comparison dynamically
                val text1 = getBookContentSnippet(b1).ifEmpty { "Текст книги 1 недоступен" }
                val text2 = getBookContentSnippet(b2).ifEmpty { "Текст книги 2 недоступен" }
                
                val result = LocalAIManager.compareTexts(requireContext(), text1, text2)
                
                binding.tvComparisonResult.text = result
                binding.scrollViewResult.visibility = View.VISIBLE
            } catch (e: Exception) {
                AiToast.show(requireContext(), "Ошибка: ${e.message}", ToastType.ERROR)
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnCompare.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class BookPickerAdapter(
        private var books: List<BookEntity>,
        private val onItemClick: (BookEntity) -> Unit
    ) : RecyclerView.Adapter<BookPickerAdapter.ViewHolder>() {

        private var filteredBooks = books

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvBookTitle)
            val tvAuthor: TextView = view.findViewById(R.id.tvBookAuthor)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_book_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val book = filteredBooks[position]
            holder.tvTitle.text = book.title
            holder.tvAuthor.text = book.author ?: "Неизвестный автор"
            holder.itemView.setOnClickListener { onItemClick(book) }
        }

        override fun getItemCount(): Int = filteredBooks.size

        fun filter(query: String) {
            filteredBooks = if (query.isEmpty()) {
                books
            } else {
                val lowerQuery = query.lowercase(Locale.ROOT)
                books.filter {
                    it.title.lowercase(Locale.ROOT).contains(lowerQuery) ||
                            (it.author?.lowercase(Locale.ROOT)?.contains(lowerQuery) ?: false)
                }
            }
            notifyDataSetChanged()
        }
    }
}
