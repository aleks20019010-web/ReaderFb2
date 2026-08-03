package com.nightread.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.nightread.app.R
import com.nightread.app.data.BookEntity
import java.io.File

class AudiobookAdapter(
    private var audiobooks: List<BookEntity>,
    private val onItemClick: (BookEntity) -> Unit
) : RecyclerView.Adapter<AudiobookAdapter.AudiobookViewHolder>() {

    class AudiobookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCover: ImageView = itemView.findViewById(R.id.ivAudioCover)
        val tvTitle: TextView = itemView.findViewById(R.id.tvAudioTitle)
        val tvAuthor: TextView = itemView.findViewById(R.id.tvAudioAuthor)
        val tvDuration: TextView = itemView.findViewById(R.id.tvAudioDuration)
        val btnPlay: ImageButton = itemView.findViewById(R.id.btnPlayAudiobook)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudiobookViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_audiobook, parent, false)
        return AudiobookViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudiobookViewHolder, position: Int) {
        val book = audiobooks[position]
        holder.tvTitle.text = book.title
        holder.tvAuthor.text = book.author ?: "Неизвестный исполнитель"

        if (!book.coverPath.isNullOrEmpty() && File(book.coverPath).exists()) {
            holder.ivCover.load(File(book.coverPath))
        } else {
            holder.ivCover.setImageResource(R.drawable.ic_headphones)
        }

        val sizeMb = if (book.fileSize > 0) String.format("%.1f MB", book.fileSize / (1024f * 1024f)) else "Аудио"
        val durMs = if (book.totalCharacters > 0) book.totalCharacters else getDurationMs(book.filePath)
        val formattedDur = if (durMs > 0) formatMs(durMs) else null

        holder.tvDuration.text = if (formattedDur != null) "$formattedDur • $sizeMb" else sizeMb

        holder.itemView.setOnClickListener { onItemClick(book) }
        holder.btnPlay.setOnClickListener { onItemClick(book) }
    }

    private fun getDurationMs(path: String?): Int {
        if (path.isNullOrEmpty()) return 0
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(path)
            val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            timeStr?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun formatMs(ms: Int): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%d ч %02d мин", hours, minutes)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun getItemCount(): Int = audiobooks.size

    fun updateData(newAudiobooks: List<BookEntity>) {
        audiobooks = newAudiobooks
        notifyDataSetChanged()
    }
}
