package com.nightread.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.R
import com.nightread.app.data.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private val onNoteClicked: (NoteEntity) -> Unit,
    private val onNoteDeleteClicked: (NoteEntity) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private var notesList: List<NoteEntity> = emptyList()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun submitList(newList: List<NoteEntity>) {
        notesList = newList
        notifyDataSetChanged()
    }

    fun getNoteAt(position: Int): NoteEntity? {
        return notesList.getOrNull(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notesList[position]
        holder.bind(note)
    }

    override fun getItemCount(): Int = notesList.size

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBookTitle: TextView = itemView.findViewById(R.id.tvNoteBookTitle)
        private val tvPageAndDate: TextView = itemView.findViewById(R.id.tvNotePageAndDate)
        private val tvSelectedText: TextView = itemView.findViewById(R.id.tvSelectedText)
        private val tvNoteText: TextView = itemView.findViewById(R.id.tvNoteText)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteNote)

        fun bind(note: NoteEntity) {
            tvBookTitle.text = note.bookTitle
            
            val dateStr = dateFormat.format(Date(note.timestamp))
            tvPageAndDate.text = dateStr
            
            tvSelectedText.text = note.selectedText.trim().ifEmpty { "(Текст не выбран)" }
            tvNoteText.text = note.noteText

            itemView.setOnClickListener {
                onNoteClicked(note)
            }

            btnDelete.setOnClickListener {
                onNoteDeleteClicked(note)
            }
        }
    }
}
