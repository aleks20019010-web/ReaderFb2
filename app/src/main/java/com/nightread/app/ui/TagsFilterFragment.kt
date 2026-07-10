package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.nightread.app.R
import com.nightread.app.data.BookEntity
import com.nightread.app.data.BookRepository
import com.nightread.app.data.AppDatabase
import com.nightread.app.databinding.FragmentTagsFilterBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TagsFilterFragment : Fragment() {

    private var _binding: FragmentTagsFilterBinding? = null
    private val binding get() = _binding!!

    private lateinit var bookRepository: BookRepository
    private var allBooks: List<BookEntity> = emptyList()
    private var selectedTag: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTagsFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        bookRepository = BookRepository(db.bookDao(), db.noteDao())

        setupToolbar()
        setupRecyclerView()
        loadTagsAndBooks()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        binding.rvFilteredBooks.layoutManager = GridLayoutManager(requireContext(), 3)
        // We can reuse BookAdapter from LibraryFragment
        binding.rvFilteredBooks.adapter = BookAdapter(emptyList(), { book ->
            // Open book detail
            val intent = android.content.Intent(requireContext(), BookDetailActivity::class.java).apply {
                putExtra("BOOK_SHA1", book.sha1)
            }
            startActivity(intent)
        })
    }

    private fun loadTagsAndBooks() {
        lifecycleScope.launch {
            allBooks = bookRepository.allBooks.first()
            val tagMap = mutableMapOf<String, Int>()

            allBooks.forEach { book ->
                book.tags?.split(",")?.forEach { tag ->
                    val cleanTag = tag.trim()
                    if (cleanTag.isNotEmpty()) {
                        tagMap[cleanTag] = tagMap.getOrDefault(cleanTag, 0) + 1
                    }
                }
            }

            binding.chipGroupTags.removeAllViews()
            
            // Add "All" chip
            val allChip = createChip("Все", allBooks.size)
            allChip.isChecked = true
            allChip.setOnClickListener { 
                selectedTag = null
                updateFilteredList()
            }
            binding.chipGroupTags.addView(allChip)

            tagMap.forEach { (tag, count) ->
                val chip = createChip(tag, count)
                chip.setOnClickListener {
                    selectedTag = tag
                    updateFilteredList()
                }
                binding.chipGroupTags.addView(chip)
            }

            updateFilteredList()
        }
    }

    private fun createChip(label: String, count: Int): Chip {
        return Chip(requireContext()).apply {
            text = "$label ($count)"
            isCheckable = true
            setChipBackgroundColorResource(R.color.bg_card)
            setTextColor(resources.getColor(R.color.text_primary))
            setChipStrokeColorResource(R.color.divider)
            setChipStrokeWidth(1f)
        }
    }

    private fun updateFilteredList() {
        val filtered = if (selectedTag == null) {
            allBooks
        } else {
            allBooks.filter { it.tags?.contains(selectedTag!!) == true }
        }
        (binding.rvFilteredBooks.adapter as BookAdapter).updateData(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
