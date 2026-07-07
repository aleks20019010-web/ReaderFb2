package com.nightread.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.os.Build
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.YandexDiskManager
import com.nightread.app.data.YandexSyncManager
import com.nightread.app.data.YandexSyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Фрагмент синхронизации с Яндекс Диском.
 * Отображает статус подключения к диску, параметры и подробный интерактивный прогресс синхронизации,
 * выполняющейся в фоновом режиме через WorkManager.
 */
class YandexSyncFragment : Fragment() {

    companion object {
        private const val TAG = "YandexSyncFragment"
        private const val WORK_NAME = "YandexDiskSync"

        fun newInstance(): YandexSyncFragment {
            return YandexSyncFragment()
        }
    }

    private lateinit var btnMenu: ImageButton
    private lateinit var statusValue: TextView
    private lateinit var layoutStorage: LinearLayout
    private lateinit var txtUsername: TextView
    private lateinit var txtStorage: TextView
    private lateinit var progressStorage: ProgressBar
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button

    private lateinit var cardSync: View
    private lateinit var txtSyncFolder: TextView
    private lateinit var btnSelectFolder: Button
    private lateinit var btnSyncNow: Button
    
    // Элементы прогресса
    private lateinit var layoutSyncProgress: LinearLayout
    private lateinit var txtSyncStatus: TextView
    private lateinit var progressSync: ProgressBar
    private lateinit var txtSyncProgressCount: TextView
    private lateinit var txtRemainingTime: TextView
    private lateinit var txtSyncStatsDetail: TextView
    private lateinit var btnCancelSync: Button
    
    private lateinit var txtLastSync: TextView

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), "Авторизация прошла успешно!", Toast.LENGTH_SHORT).show()
            updateUi()
            startForegroundSync()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sync, container, false)

        // Инициализация view элементов
        btnMenu = view.findViewById(R.id.btnMenu)
        statusValue = view.findViewById(R.id.statusValue)
        layoutStorage = view.findViewById(R.id.layoutStorage)
        txtUsername = view.findViewById(R.id.txtUsername)
        txtStorage = view.findViewById(R.id.txtStorage)
        progressStorage = view.findViewById(R.id.progressStorage)
        btnConnect = view.findViewById(R.id.btnConnect)
        btnDisconnect = view.findViewById(R.id.btnDisconnect)

        cardSync = view.findViewById(R.id.cardSync)
        btnSyncNow = view.findViewById(R.id.btnSyncNow)
        txtSyncFolder = view.findViewById(R.id.txtSyncFolder)
        btnSelectFolder = view.findViewById(R.id.btnSelectFolder)
        
        layoutSyncProgress = view.findViewById(R.id.layoutSyncProgress)
        txtSyncStatus = view.findViewById(R.id.txtSyncStatus)
        progressSync = view.findViewById(R.id.progressSync)
        txtSyncProgressCount = view.findViewById(R.id.txtSyncProgressCount)
        txtRemainingTime = view.findViewById(R.id.txtRemainingTime)
        txtSyncStatsDetail = view.findViewById(R.id.txtSyncStatsDetail)
        btnCancelSync = view.findViewById(R.id.btnCancelSync)
        
        txtLastSync = view.findViewById(R.id.txtLastSync)

        // Навешивание слушателей событий
        btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        btnConnect.setOnClickListener {
            val intent = Intent(requireContext(), YandexAuthActivity::class.java)
            authLauncher.launch(intent)
        }

        btnDisconnect.setOnClickListener {
            YandexDiskManager.clearToken(requireContext())
            Toast.makeText(requireContext(), "Вы успешно вышли из аккаунта", Toast.LENGTH_SHORT).show()
            updateUi()
        }

        btnSelectFolder.setOnClickListener {
            showFolderSelectionDialog()
        }

        btnSyncNow.setOnClickListener {
            startForegroundSync()
        }

        btnCancelSync.setOnClickListener {
            cancelForegroundSync()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeSyncState()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        val context = requireContext()
        val authorized = YandexDiskManager.isAuthorized(context)

        if (authorized) {
            statusValue.text = "Подключено"
            statusValue.setTextColor(resources.getColor(R.color.accent, null))
            btnConnect.visibility = View.GONE
            btnDisconnect.visibility = View.VISIBLE
            layoutStorage.visibility = View.VISIBLE
            cardSync.visibility = View.VISIBLE

            val currentFolder = YandexDiskManager.getSyncFolder(context)
            txtSyncFolder.text = "Папка: $currentFolder"

            // Загрузка информации о диске
            lifecycleScope.launch {
                try {
                    val info = YandexDiskManager.getDiskInfo(context)
                    txtUsername.text = "Пользователь: ${info.user?.displayName ?: info.user?.login ?: "Неизвестен"}"
                    
                    val usedStr = Formatter.formatFileSize(context, info.usedSpace)
                    val totalStr = Formatter.formatFileSize(context, info.totalSpace)
                    txtStorage.text = "Занято: $usedStr из $totalStr"

                    val percent = if (info.totalSpace > 0) {
                        ((info.usedSpace.toDouble() / info.totalSpace) * 100).toInt()
                    } else {
                        0
                    }
                    progressStorage.progress = percent
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading disk info", e)
                }
            }

            // Обновление метки последней синхронизации
            refreshLastSyncTime()
        } else {
            statusValue.text = "Не авторизован"
            statusValue.setTextColor(resources.getColor(R.color.text_secondary, null))
            btnConnect.visibility = View.VISIBLE
            btnDisconnect.visibility = View.GONE
            layoutStorage.visibility = View.GONE
            cardSync.visibility = View.GONE
            layoutSyncProgress.visibility = View.GONE
        }
    }

    private fun refreshLastSyncTime() {
        val lastSync = YandexDiskManager.getLastSyncTimestamp(requireContext())
        if (lastSync > 0L) {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            txtLastSync.text = "Последняя синхронизация: ${sdf.format(Date(lastSync))}"
        } else {
            txtLastSync.text = "Последняя синхронизация: никогда"
        }
    }

    /**
     * Запуск фоновой синхронизации через Foreground Service.
     */
    private fun startForegroundSync() {
        val context = requireContext()
        val syncManager = YandexSyncManager(context)

        if (!syncManager.hasInternetConnection()) {
            Toast.makeText(context, "Отсутствует подключение к интернету", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, com.nightread.app.service.SyncService::class.java).apply {
            action = com.nightread.app.service.SyncService.ACTION_START_SYNC
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Toast.makeText(context, "Синхронизация запущена в фоновом режиме", Toast.LENGTH_SHORT).show()
    }

    /**
     * Отмена фоновой синхронизации в Foreground Service.
     */
    private fun cancelForegroundSync() {
        val context = requireContext()
        val intent = Intent(context, com.nightread.app.service.SyncService::class.java).apply {
            action = com.nightread.app.service.SyncService.ACTION_STOP_SYNC
        }
        context.startService(intent)
        Toast.makeText(context, "Запрос на отмену отправлен", Toast.LENGTH_SHORT).show()
    }

    /**
     * Подписка на обновление состояния фоновой синхронизации в реальном времени.
     */
    private fun observeSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            YandexSyncState.state.collectLatest { state ->
                if (!isAdded) return@collectLatest

                if (state.isRunning) {
                    layoutSyncProgress.visibility = View.VISIBLE
                    btnSyncNow.isEnabled = false
                    btnSelectFolder.isEnabled = false

                    txtSyncStatus.text = state.statusText
                    
                    if (state.stage == YandexSyncState.Stage.SCANNING || state.stage == YandexSyncState.Stage.PREPARING) {
                        progressSync.isIndeterminate = true
                        txtSyncProgressCount.text = ""
                    } else {
                        progressSync.isIndeterminate = false
                        progressSync.max = state.total
                        progressSync.progress = state.completed
                        txtSyncProgressCount.text = "${state.completed} / ${state.total} (${state.percent}%)"
                    }

                    // Отображение оставшегося времени
                    if (state.remainingTimeSeconds >= 0L) {
                        val mins = state.remainingTimeSeconds / 60
                        val secs = state.remainingTimeSeconds % 60
                        val timeStr = if (mins > 0) "${mins}м ${secs}с" else "${secs}с"
                        txtRemainingTime.text = "Примерное оставшееся время: $timeStr"
                        txtRemainingTime.visibility = View.VISIBLE
                    } else {
                        if (state.stage == YandexSyncState.Stage.PROGRESS_SYNC) {
                            txtRemainingTime.text = "Обмен прогрессом чтения..."
                        } else {
                            txtRemainingTime.text = "Оставшееся время: расчет..."
                        }
                        txtRemainingTime.visibility = View.VISIBLE
                    }

                    // Сводная статистика
                    txtSyncStatsDetail.text = "Скачано: ${state.downloadedCount} книг | Загружено: ${state.uploadedCount} книг"
                    txtSyncStatsDetail.visibility = View.VISIBLE
                    btnCancelSync.visibility = View.VISIBLE
                } else {
                    // Синхронизация не запущена или только что завершилась
                    if (state.finished) {
                        // Показываем отчет при завершении
                        if (state.success) {
                            android.app.AlertDialog.Builder(requireContext())
                                .setTitle("Синхронизация завершена")
                                .setMessage("Синхронизация с Яндекс Диском успешно выполнена!\n\n" +
                                        "Скачано новых книг: ${state.downloadedCount}\n" +
                                        "Загружено книг в облако: ${state.uploadedCount}")
                                .setPositiveButton("Отлично", null)
                                .show()
                        } else {
                            val errorMsg = state.error ?: "Неизвестная ошибка во время синхронизации."
                            if (errorMsg != "Синхронизация отменена") {
                                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                        
                        // Сбрасываем завершенное состояние, чтобы уведомление не показывалось повторно
                        YandexSyncState.reset()
                        refreshLastSyncTime()
                    }

                    // Возвращаем стандартный UI
                    btnSyncNow.isEnabled = true
                    btnSelectFolder.isEnabled = true
                    layoutSyncProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun showFolderSelectionDialog() {
        val context = requireContext()
        val syncManager = YandexSyncManager(context)
        if (!syncManager.hasInternetConnection()) {
            Toast.makeText(context, "Отсутствует подключение к интернету", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val pd = android.app.ProgressDialog(context)
            pd.setMessage("Загрузка папок...")
            pd.setCancelable(false)
            pd.show()
            
            val folders = withContext(Dispatchers.IO) {
                YandexDiskManager.getFolders(context, "/")
            }
            pd.dismiss()
            
            val folderNames = folders.map { it.name }.toMutableList()
            folderNames.add(0, "/") // Корневая папка
            
            val builder = android.app.AlertDialog.Builder(context)
            builder.setTitle("Выберите папку на диске")
            builder.setItems(folderNames.toTypedArray()) { _, which ->
                val selectedPath = if (which == 0) "/" else folders[which - 1].path ?: "/Books"
                YandexDiskManager.setSyncFolder(context, selectedPath)
                updateUi()
            }
            builder.setNegativeButton("Отмена", null)
            builder.show()
        }
    }
}
