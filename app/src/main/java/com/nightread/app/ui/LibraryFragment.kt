package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nightread.app.R
import com.nightread.app.data.BookEntity

class LibraryFragment : Fragment() {

    private val viewModel: BookViewModel by activityViewModels()

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
            androidx.compose.material3.MaterialTheme {
                val books by viewModel.allBooks.collectAsState(initial = emptyList())

                LibraryComposeUI(
                    books = books,
                    onScanClicked = { viewModel.startLocalBookScan() },
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
            }
        }
    }
}
