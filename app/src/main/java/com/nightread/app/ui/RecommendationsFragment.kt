package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.service.LocalAIManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecommendationsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recommendations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvRecommendations = view.findViewById<RecyclerView>(R.id.rvRecommendations)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        rvRecommendations.layoutManager = GridLayoutManager(context, 3)
        
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            
            val db = AppDatabase.getDatabase(requireContext())
            val allBooks = withContext(Dispatchers.IO) { db.bookDao().getAllBooksSync() }
            val readBooks = allBooks.filter { it.currentPageIndex > 0 } // Simple criteria for "read"

            if (allBooks.isEmpty()) {
                progressBar.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                return@launch
            }

            val recommended = LocalAIManager.getRecommendations(requireContext(), readBooks, allBooks)
            
            if (recommended.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                // We use the same BookAdapter as LibraryFragment if possible, 
                // but since it might be complex to re-use, let's assume BookAdapter is available
                rvRecommendations.adapter = BookAdapter(
                    books = recommended,
                    onOpenBook = { book ->
                        val intent = android.content.Intent(requireContext(), BookDetailActivity::class.java)
                        intent.putExtra("sha1", book.sha1)
                        startActivity(intent)
                    }
                )
            }
            
            progressBar.visibility = View.GONE
        }
    }
}
