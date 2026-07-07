package com.nightread.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookEntity
import com.nightread.app.data.CleanupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanupDialogFragment : DialogFragment(R.layout.dialog_cleanup) {

    private lateinit var rvDuplicates: RecyclerView
    private lateinit var btnDeleteSelected: Button
    private lateinit var btnCancel: Button
    private lateinit var cleanupManager: CleanupManager
    private var duplicatesMap = mapOf<String, List<BookEntity>>()
    private val selectedFiles = mutableSetOf<BookEntity>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        rvDuplicates = view.findViewById(R.id.rvDuplicates)
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected)
        btnCancel = view.findViewById(R.id.btnCancel)
        
        val db = AppDatabase.getDatabase(requireContext())
        cleanupManager = CleanupManager(db.bookDao())

        rvDuplicates.layoutManager = LinearLayoutManager(requireContext())
        
        CoroutineScope(Dispatchers.Main).launch {
            duplicatesMap = cleanupManager.getDuplicates()
            setupRecyclerView()
        }

        btnCancel.setOnClickListener { dismiss() }
        
        btnDeleteSelected.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val result = cleanupManager.deleteBooks(selectedFiles.toList())
                Toast.makeText(requireContext(), "Удалено: ${result.first}, освобождено: ${result.second} байт", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private fun setupRecyclerView() {
        // Simple Adapter implementation would be here
    }
}
