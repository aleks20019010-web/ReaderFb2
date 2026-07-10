package com.nightread.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.AiModel
import com.nightread.app.service.LocalAIManager
import java.io.File

class ModelListAdapter(
    private val models: List<AiModel>,
    private val modelsDir: File,
    private val activeModelId: String?,
    private val onDownloadClick: (AiModel) -> Unit,
    private val onDeleteClick: (AiModel) -> Unit,
    private val onSelectClick: (AiModel) -> Unit
) : RecyclerView.Adapter<ModelListAdapter.ViewHolder>() {

    private val downloadProgressMap = mutableMapOf<String, Int>()

    fun updateProgress(modelId: String, progress: Int) {
        downloadProgressMap[modelId] = progress
        val index = models.indexOfFirst { it.id == modelId }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        val file = File(modelsDir, model.fileName)
        val isDownloaded = file.exists()
        val isActive = model.id == activeModelId
        val progress = downloadProgressMap[model.id]

        holder.tvName.text = model.name
        holder.tvSize.text = model.sizeStr
        holder.tvDesc.text = model.description

        if (isDownloaded) {
            holder.pbDownload.visibility = View.GONE
            holder.btnDelete.visibility = View.VISIBLE
            
            if (isActive) {
                holder.tvStatus.visibility = View.VISIBLE
                if (LocalAIManager.isModelLoaded) {
                    holder.tvStatus.text = "Модель загружена"
                } else {
                    holder.tvStatus.text = "Модель не загружена"
                }
                holder.btnAction.text = "Выбрано"
                holder.btnAction.isEnabled = false
            } else {
                holder.tvStatus.visibility = View.GONE
                holder.btnAction.text = "Выбрать"
                holder.btnAction.isEnabled = true
                holder.btnAction.setOnClickListener { onSelectClick(model) }
            }
            
            holder.btnDelete.setOnClickListener { onDeleteClick(model) }
        } else {
            holder.btnDelete.visibility = View.GONE
            holder.tvStatus.visibility = View.GONE
            
            if (progress != null && progress < 100) {
                holder.pbDownload.visibility = View.VISIBLE
                holder.pbDownload.progress = progress
                holder.tvStatus.visibility = View.VISIBLE
                holder.tvStatus.text = "Скачивание: $progress%"
                holder.btnAction.text = "Скачивание..."
                holder.btnAction.isEnabled = false
            } else {
                holder.pbDownload.visibility = View.GONE
                holder.btnAction.text = "Скачать"
                holder.btnAction.isEnabled = true
                holder.btnAction.setOnClickListener { onDownloadClick(model) }
            }
        }
    }

    override fun getItemCount(): Int = models.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_model_name)
        val tvSize: TextView = view.findViewById(R.id.tv_model_size)
        val tvDesc: TextView = view.findViewById(R.id.tv_model_desc)
        val tvStatus: TextView = view.findViewById(R.id.tv_status)
        val pbDownload: ProgressBar = view.findViewById(R.id.pb_download)
        val btnAction: Button = view.findViewById(R.id.btn_action)
        val btnDelete: Button = view.findViewById(R.id.btn_delete)
    }
}
