package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.databinding.BottomSheetQaBinding
import com.nightread.app.service.LocalAIManager
import kotlinx.coroutines.launch

class QABottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetQaBinding? = null
    private val binding get() = _binding!!

    private val messages = mutableListOf<QAMessage>()
    private lateinit var adapter: QAAdapter

    data class QAMessage(val text: String, val isUser: Boolean)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetQaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = QAAdapter(messages)
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChat.adapter = adapter

        binding.btnSend.setOnClickListener {
            val question = binding.etQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                askQuestion(question)
            }
        }
    }

    private fun askQuestion(question: String) {
        if (!LocalAIManager.isModelLoaded) {
            Toast.makeText(requireContext(), "AI модель не загружена", Toast.LENGTH_SHORT).show()
            return
        }

        messages.add(QAMessage(question, true))
        adapter.notifyItemInserted(messages.size - 1)
        binding.rvChat.scrollToPosition(messages.size - 1)
        binding.etQuestion.setText("")

        lifecycleScope.launch {
            val bookText = BookCache.content
            if (bookText.isEmpty()) {
                addResponse("Текст книги не загружен в кэш. Пожалуйста, откройте книгу еще раз.")
                return@launch
            }

            // Show "typing" or similar if needed
            val answer = LocalAIManager.getQuestionAnswer(requireContext(), question, bookText.take(15000))
            addResponse(answer)
        }
    }

    private fun addResponse(text: String) {
        messages.add(QAMessage(text, false))
        adapter.notifyItemInserted(messages.size - 1)
        binding.rvChat.scrollToPosition(messages.size - 1)
    }

    inner class QAAdapter(private val list: List<QAMessage>) : RecyclerView.Adapter<QAAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        }

        override fun getItemViewType(position: Int): Int {
            return if (list[position].isUser) 1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = if (viewType == 1) R.layout.item_chat_user else R.layout.item_chat_ai
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.tvMessage.text = list[position].text
        }

        override fun getItemCount() = list.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
