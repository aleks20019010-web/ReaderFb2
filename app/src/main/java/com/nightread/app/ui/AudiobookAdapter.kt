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
        holder.tvDuration.text = sizeMb

        holder.itemView.setOnClickListener { onItemClick(book) }
        holder.btnPlay.setOnClickListener { onItemClick(book) }
    }

    override fun getItemCount(): Int = audiobooks.size

    fun updateData(newAudiobooks: List<BookEntity>) {
        audiobooks = newAudiobooks
        notifyDataSetChanged()
    }
}
