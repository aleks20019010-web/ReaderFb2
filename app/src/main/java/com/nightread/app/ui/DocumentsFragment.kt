package com.nightread.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.BookEntity
import com.nightread.app.service.ScanState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DocumentsFragment : Fragment() {

    companion object {
        private const val TAG = "DocumentsFragment"
        fun newInstance(): DocumentsFragment = DocumentsFragment()
    }

    private val viewModel: BookViewModel by lazy {
        ViewModelProvider(requireActivity())[BookViewModel::class.java]
    }

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvDocuments: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var tvDocumentCount: TextView
    private lateinit var btnMenu: ImageButton

    private lateinit var adapter: BookAdapter

    // Manage storage permission launcher for Android 11+
    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            startDocumentScan()
        } else {
            Toast.makeText(context, "Разрешение на доступ ко всем файлам отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    // Standard storage permission launcher
    private val requestStandardPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startDocumentScan()
        } else {
            Toast.makeText(context, "Доступ к хранилищу отклонен", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_documents, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnMenu = view.findViewById(R.id.btnMenu)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        rvDocuments = view.findViewById(R.id.rvDocuments)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        tvDocumentCount = view.findViewById(R.id.tvDocumentCount)

        // Setup Drawer Menu Button
        btnMenu.setOnClickListener {
            val mainActivity = activity as? MainActivity
            mainActivity?.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
                ?.openDrawer(GravityCompat.START)
        }

        // Setup RecyclerView
        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book, coverView ->
                val intent = Intent(requireContext(), BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                startActivity(intent)
            }
        )
        rvDocuments.layoutManager = LinearLayoutManager(requireContext())
        rvDocuments.adapter = adapter

        // Setup Refresh Theme Style
        swipeRefresh.setColorSchemeResources(R.color.accent, R.color.text_primary)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.bg_card)

        swipeRefresh.setOnRefreshListener {
            checkPermissionsAndScan()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        // Collect scanned documents flow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allDocuments.collectLatest { documents ->
                adapter.updateData(documents, false)
                updateDocumentCount(documents.size)

                if (documents.isEmpty()) {
                    rvDocuments.visibility = View.GONE
                    layoutEmpty.visibility = View.VISIBLE
                } else {
                    rvDocuments.visibility = View.VISIBLE
                    layoutEmpty.visibility = View.GONE
                }
            }
        }

        // Observe scanning state to stop refresh layout
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
                swipeRefresh.isRefreshing = state.isScanning
            }
        }
    }

    private fun updateDocumentCount(count: Int) {
        val remainder10 = count % 10
        val remainder100 = count % 100
        val text = when {
            remainder100 in 11..19 -> "$count документов"
            remainder10 == 1 -> "$count документ"
            remainder10 in 2..4 -> "$count документа"
            else -> "$count документов"
        }
        tvDocumentCount.text = text
    }

    private fun checkPermissionsAndScan() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startDocumentScan()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                    requestManageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        requestManageStorageLauncher.launch(intent)
                    } catch (ex: Exception) {
                        requestStandardPermission()
                    }
                }
            }
        } else {
            requestStandardPermission()
        }
    }

    private fun requestStandardPermission() {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startDocumentScan()
        } else {
            requestStandardPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun startDocumentScan() {
        if (viewModel.scanState.value.isScanning) {
            Toast.makeText(context, "Сканирование уже выполняется", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.startIncrementalBookScan()
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { com.nightread.app.ui.GalaxyBgHelper.applyBackground(it) }
    }
}
