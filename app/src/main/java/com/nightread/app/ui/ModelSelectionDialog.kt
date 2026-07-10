package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nightread.app.data.AVAILABLE_AI_MODELS
import com.nightread.app.data.AiModel
import com.nightread.app.databinding.DialogModelSelectionBinding
import java.io.File

class ModelSelectionDialog(
    private val onModelSelected: (AiModel) -> Unit
) : DialogFragment() {

    private var _binding: DialogModelSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogModelSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val modelsDir = File(requireContext().filesDir, "ai_models")
        
        binding.rvModels.layoutManager = LinearLayoutManager(requireContext())
        binding.rvModels.adapter = ModelListAdapter(
            models = AVAILABLE_AI_MODELS,
            modelsDir = modelsDir,
            activeModelId = null, // In this dialog we just pick to download
            onDownloadClick = { model -> 
                onModelSelected(model)
                dismiss()
            },
            onDeleteClick = {},
            onSelectClick = {}
        )

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
