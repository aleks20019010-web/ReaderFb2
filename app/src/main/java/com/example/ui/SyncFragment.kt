package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
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
import com.example.MainActivity
import com.example.R
import com.example.data.YandexDiskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncFragment : Fragment() {

    companion object {
        fun newInstance(): SyncFragment {
            return SyncFragment()
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
    private lateinit var btnSyncNow: Button
    private lateinit var layoutSyncProgress: LinearLayout
    private lateinit var txtSyncStatus: TextView
    private lateinit var progressSync: ProgressBar
    private lateinit var txtSyncProgressCount: TextView
    private lateinit var txtLastSync: TextView

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), "Авторизация прошла успешно!", Toast.LENGTH_SHORT).show()
            updateUi()
            triggerSync()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sync, container, false)

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
        layoutSyncProgress = view.findViewById(R.id.layoutSyncProgress)
        txtSyncStatus = view.findViewById(R.id.txtSyncStatus)
        progressSync = view.findViewById(R.id.progressSync)
        txtSyncProgressCount = view.findViewById(R.id.txtSyncProgressCount)
        txtLastSync = view.findViewById(R.id.txtLastSync)

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

        btnSyncNow.setOnClickListener {
            triggerSync()
        }

        return view
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

            // Fetch Disk info
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
                    android.util.Log.e("SyncFragment", "Error loading disk info", e)
                }
            }

            // Update Last Sync Info
            val lastSync = YandexDiskManager.getLastSyncTimestamp(context)
            if (lastSync > 0L) {
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                txtLastSync.text = "Последняя синхронизация: ${sdf.format(Date(lastSync))}"
            } else {
                txtLastSync.text = "Последняя синхронизация: никогда"
            }
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

    private fun triggerSync() {
        val context = requireContext()
        btnSyncNow.isEnabled = false
        layoutSyncProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val success = YandexDiskManager.syncWithCloud(context) { status, completed, total ->
                lifecycleScope.launch(Dispatchers.Main) {
                    txtSyncStatus.text = status
                    if (total > 0) {
                        progressSync.max = total
                        progressSync.progress = completed
                        txtSyncProgressCount.text = "$completed / $total"
                    } else {
                        progressSync.max = 100
                        progressSync.progress = 0
                        txtSyncProgressCount.text = ""
                    }
                }
            }

            if (success) {
                Toast.makeText(context, "Синхронизация завершена успешно!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Ошибка при синхронизации", Toast.LENGTH_SHORT).show()
            }

            btnSyncNow.isEnabled = true
            updateUi()
        }
    }
}
