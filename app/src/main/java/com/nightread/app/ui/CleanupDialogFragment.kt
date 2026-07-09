package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.CleanupManager
import com.nightread.app.data.DuplicateFile
import com.nightread.app.data.DuplicateGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanupDialogFragment : DialogFragment() {

    private lateinit var layoutProgress: LinearLayout
    private lateinit var tvProgressStatus: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var rvDuplicates: RecyclerView
    private lateinit var btnDeleteSelected: Button
    private lateinit var btnCancel: Button

    private lateinit var cleanupManager: CleanupManager
    private var duplicateGroups = listOf<DuplicateGroup>()
    private var adapter: DuplicatesAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_cleanup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutProgress = view.findViewById(R.id.layoutProgress)
        tvProgressStatus = view.findViewById(R.id.tvProgressStatus)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        rvDuplicates = view.findViewById(R.id.rvDuplicates)
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected)
        btnCancel = view.findViewById(R.id.btnCancel)

        val db = AppDatabase.getDatabase(requireContext())
        cleanupManager = CleanupManager(requireContext(), db.bookDao())

        rvDuplicates.layoutManager = LinearLayoutManager(requireContext())

        btnCancel.setOnClickListener { dismiss() }

        btnDeleteSelected.setOnClickListener {
            performCleanup()
        }

        startDuplicateScan()
    }

    private fun startDuplicateScan() {
        layoutProgress.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE
        rvDuplicates.visibility = View.GONE
        btnDeleteSelected.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    cleanupManager.findDuplicates { current, total, status ->
                        CoroutineScope(Dispatchers.Main).launch {
                            tvProgressStatus.text = status
                        }
                    }
                }

                duplicateGroups = results
                layoutProgress.visibility = View.GONE

                if (duplicateGroups.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    btnDeleteSelected.isEnabled = false
                } else {
                    rvDuplicates.visibility = View.VISIBLE
                    btnDeleteSelected.isEnabled = true
                    setupRecyclerView()
                }
            } catch (e: Exception) {
                layoutProgress.visibility = View.GONE
                tvEmptyState.text = "Ошибка при поиске: ${e.localizedMessage}"
                tvEmptyState.visibility = View.VISIBLE
            }
        }
    }

    private fun setupRecyclerView() {
        val flatItems = buildFlatList()
        adapter = DuplicatesAdapter(flatItems) { file, isChecked, group ->
            // Callback for selection changes, but the safety check is handled in the binding logic
            updateDeleteButtonState()
        }
        rvDuplicates.adapter = adapter
        updateDeleteButtonState()
    }

    private fun buildFlatList(): List<DuplicateListItem> {
        val list = mutableListOf<DuplicateListItem>()
        for (group in duplicateGroups) {
            list.add(DuplicateListItem.Header(group))
            for (file in group.files) {
                list.add(DuplicateListItem.Child(file, group))
            }
        }
        return list
    }

    private fun updateDeleteButtonState() {
        val selectedCount = duplicateGroups.flatMap { it.files }.count { it.isSelected }
        if (selectedCount > 0) {
            btnDeleteSelected.text = "Удалить выбранные ($selectedCount)"
            btnDeleteSelected.isEnabled = true
        } else {
            btnDeleteSelected.text = "Удалить выбранные"
            btnDeleteSelected.isEnabled = false
        }
    }

    private fun performCleanup() {
        val filesToDelete = duplicateGroups.flatMap { it.files }.filter { it.isSelected }
        if (filesToDelete.isEmpty()) {
            CustomToast.show(requireContext(), "Ничего не выбрано для удаления")
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение удаления")
            .setMessage("Вы уверены, что хотите удалить выбранные файлы (${filesToDelete.size} шт.)? Это действие необратимо.")
            .setPositiveButton("Удалить") { _, _ ->
                executeDeletion(filesToDelete)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun executeDeletion(filesToDelete: List<DuplicateFile>) {
        layoutProgress.visibility = View.VISIBLE
        tvProgressStatus.text = "Удаление файлов..."
        rvDuplicates.visibility = View.GONE
        btnDeleteSelected.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    cleanupManager.executeCleanup(filesToDelete, duplicateGroups)
                }
                layoutProgress.visibility = View.GONE
                
                // Format size
                val sizeStr = formatSize(result.second)
                CustomToast.show(
                    requireContext(),
                    "Успешно удалено: ${result.first} файлов. Освобождено: $sizeStr"
                )
                
                dismiss()
            } catch (e: Exception) {
                layoutProgress.visibility = View.GONE
                rvDuplicates.visibility = View.VISIBLE
                btnDeleteSelected.isEnabled = true
                CustomToast.show(requireContext(), "Ошибка при удалении: ${e.localizedMessage}")
            }
        }
    }

    private fun formatSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 Б"
        val units = arrayOf("Б", "КБ", "МБ", "ГБ")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        if (digitGroups >= units.size) return "$sizeInBytes Б"
        return String.format("%.2f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    // RecyclerView items mapping
    private sealed class DuplicateListItem {
        data class Header(val group: DuplicateGroup) : DuplicateListItem()
        data class Child(val file: DuplicateFile, val group: DuplicateGroup) : DuplicateListItem()
    }

    // RecyclerView Adapter
    private class DuplicatesAdapter(
        private var items: List<DuplicateListItem>,
        private val onCheckedChange: (DuplicateFile, Boolean, DuplicateGroup) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_CHILD = 1
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is DuplicateListItem.Header -> TYPE_HEADER
                is DuplicateListItem.Child -> TYPE_CHILD
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                val view = inflater.inflate(R.layout.item_duplicate_group, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.item_duplicate_file, parent, false)
                ChildViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is DuplicateListItem.Header -> {
                    val hHolder = holder as HeaderViewHolder
                    hHolder.tvBookTitle.text = item.group.title
                    hHolder.tvSha1.text = "SHA-1: ${item.group.sha1.take(8)}... (Автор: ${item.group.author})"
                }
                is DuplicateListItem.Child -> {
                    val cHolder = holder as ChildViewHolder
                    
                    val sizeStr = formatSize(item.file.size)
                    val pathAndSize = if (item.file.isRecommended) {
                        "${item.file.filePath} ($sizeStr) — [Рекомендуется]"
                    } else {
                        "${item.file.filePath} ($sizeStr)"
                    }
                    
                    cHolder.tvFilePath.text = pathAndSize
                    
                    cHolder.cbSelectFile.setOnCheckedChangeListener(null)
                    cHolder.cbSelectFile.isChecked = item.file.isSelected
                    
                    cHolder.cbSelectFile.setOnCheckedChangeListener { buttonView, isChecked ->
                        if (isChecked) {
                            // Check safety constraint: at least one copy must remain unchecked
                            val otherSelectedCount = item.group.files.filter { it != item.file }.count { it.isSelected }
                            if (otherSelectedCount == item.group.files.size - 1) {
                                // Reverting check
                                CustomToast.show(
                                    buttonView.context,
                                    "Нельзя удалить все копии книги! Сохраните хотя бы одну."
                                )
                                buttonView.isChecked = false
                            } else {
                                item.file.isSelected = true
                                onCheckedChange(item.file, true, item.group)
                            }
                        } else {
                            item.file.isSelected = false
                            onCheckedChange(item.file, false, item.group)
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size

        private fun formatSize(sizeInBytes: Long): String {
            if (sizeInBytes <= 0) return "0 Б"
            val units = arrayOf("Б", "КБ", "МБ", "ГБ")
            val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups >= units.size) return "$sizeInBytes Б"
            return String.format("%.2f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvBookTitle: TextView = view.findViewById(R.id.tvBookTitle)
            val tvSha1: TextView = view.findViewById(R.id.tvSha1)
        }

        class ChildViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelectFile: CheckBox = view.findViewById(R.id.cbSelectFile)
            val tvFilePath: TextView = view.findViewById(R.id.tvFilePath)
        }
    }
}
