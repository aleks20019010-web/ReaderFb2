package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.nightread.app.R
import com.nightread.app.data.AVAILABLE_AI_MODELS
import com.nightread.app.data.AiModel
import com.nightread.app.data.SettingsManager
import com.nightread.app.databinding.FragmentAiSettingsBinding
import com.nightread.app.service.LocalAIManager
import com.nightread.app.service.ModelDownloadWorker
import java.io.File
import java.util.*

class AISettingsFragment : Fragment() {

    private var _binding: FragmentAiSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ModelListAdapter
    private lateinit var modelsDir: File

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        modelsDir = File(requireContext().filesDir, "ai_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        setupToolbar()
        setupSwitch()
        setupRecyclerView()
        
        observeDownloads()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupSwitch() {
        binding.switchAiEnabled.isChecked = SettingsManager.isAiEnabled(requireContext())
        binding.switchAiEnabled.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAiEnabled(requireContext(), isChecked)
            if (!isChecked) {
                LocalAIManager.unloadModel()
            } else {
                val activePath = SettingsManager.getAiModelPath(requireContext())
                if (activePath != null && File(activePath).exists()) {
                    // Load in background
                    // LocalAIManager.loadModel(activePath)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val activeId = SettingsManager.getAiModelId(requireContext())
        adapter = ModelListAdapter(
            models = AVAILABLE_AI_MODELS,
            modelsDir = modelsDir,
            activeModelId = activeId,
            onDownloadClick = { model -> startDownload(model) },
            onDeleteClick = { model -> deleteModel(model) },
            onSelectClick = { model -> selectModel(model) }
        )
        binding.rvModels.adapter = adapter
    }

    private fun startDownload(model: AiModel) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // User can change this in UI usually, but here we enforce connected
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                "MODEL_URL" to model.url,
                "MODEL_FILENAME" to model.fileName
            ))
            .addTag("download_${model.id}")
            .build()

        WorkManager.getInstance(requireContext()).enqueueUniqueWork(
            "download_${model.id}",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )
        
        Toast.makeText(requireContext(), "Скачивание началось", Toast.LENGTH_SHORT).show()
    }

    private fun deleteModel(model: AiModel) {
        val file = File(modelsDir, model.fileName)
        if (file.exists()) {
            file.delete()
            if (SettingsManager.getAiModelId(requireContext()) == model.id) {
                SettingsManager.setAiModelId(requireContext(), null)
                SettingsManager.setAiModelPath(requireContext(), null)
                LocalAIManager.unloadModel()
            }
            adapter.notifyDataSetChanged()
            Toast.makeText(requireContext(), "Модель удалена", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectModel(model: AiModel) {
        val file = File(modelsDir, model.fileName)
        if (file.exists()) {
            SettingsManager.setAiModelId(requireContext(), model.id)
            SettingsManager.setAiModelPath(requireContext(), file.absolutePath)
            
            Toast.makeText(requireContext(), "Модель выбрана и будет загружена", Toast.LENGTH_SHORT).show()
            adapter.notifyDataSetChanged() // Refresh to show active status
            
            // Trigger load if AI is enabled
            if (SettingsManager.isAiEnabled(requireContext())) {
                // In a real app we'd show a loading indicator
            }
        }
    }

    private fun observeDownloads() {
        AVAILABLE_AI_MODELS.forEach { model ->
            WorkManager.getInstance(requireContext())
                .getWorkInfosByTagLiveData("download_${model.id}")
                .observe(viewLifecycleOwner, Observer { workInfos ->
                    if (workInfos.isNullOrEmpty()) return@Observer
                    
                    val workInfo = workInfos[0]
                    if (workInfo.state == WorkInfo.State.RUNNING) {
                        val progress = workInfo.progress.getInt("PROGRESS", 0)
                        adapter.updateProgress(model.id, progress)
                    } else if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        adapter.notifyDataSetChanged()
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        Toast.makeText(requireContext(), "Ошибка при скачивании ${model.name}", Toast.LENGTH_SHORT).show()
                        adapter.notifyDataSetChanged()
                    }
                })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
