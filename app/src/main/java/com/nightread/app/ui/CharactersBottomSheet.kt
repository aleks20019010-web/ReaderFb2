package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.service.LocalAIManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CharactersBottomSheet : BottomSheetDialogFragment() {

    private var sha1: String = ""
    private var bookText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sha1 = arguments?.getString(ARG_SHA1) ?: ""
        bookText = arguments?.getString(ARG_BOOK_TEXT) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_characters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvCharacters = view.findViewById<RecyclerView>(R.id.rvCharacters)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        rvCharacters.layoutManager = LinearLayoutManager(context)
        btnClose.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val book = withContext(Dispatchers.IO) { db.bookDao().getBookBySha1(sha1) }
            
            if (book?.characters != null) {
                val characters = parseCharacters(book.characters!!)
                rvCharacters.adapter = CharactersAdapter(characters)
                progressBar.visibility = View.GONE
            } else {
                progressBar.visibility = View.VISIBLE
                val characterList = LocalAIManager.getCharacters(requireContext(), bookText.take(15000))
                rvCharacters.adapter = CharactersAdapter(characterList)
                progressBar.visibility = View.GONE
                
                if (book != null) {
                    val serialized = serializeCharacters(characterList)
                    withContext(Dispatchers.IO) {
                        db.bookDao().updateBook(book.copy(characters = serialized))
                    }
                }
            }
        }
    }

    private fun parseCharacters(json: String): List<LocalAIManager.Character> {
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<LocalAIManager.Character>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(LocalAIManager.Character(
                    obj.getString("name"),
                    obj.getString("description"),
                    obj.getString("role")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeCharacters(list: List<LocalAIManager.Character>): String {
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("description", it.description)
            obj.put("role", it.role)
            array.put(obj)
        }
        return array.toString()
    }

    private class CharactersAdapter(
        private val list: List<LocalAIManager.Character>
    ) : RecyclerView.Adapter<CharactersAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvRole: TextView = view.findViewById(R.id.tvRole)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_character, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val character = list[position]
            holder.tvName.text = character.name
            holder.tvRole.text = character.role
            holder.tvDescription.text = character.description
        }

        override fun getItemCount(): Int = list.size
    }

    companion object {
        private const val ARG_SHA1 = "arg_sha1"
        private const val ARG_BOOK_TEXT = "arg_book_text"

        fun newInstance(sha1: String, bookText: String): CharactersBottomSheet {
            val fragment = CharactersBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_SHA1, sha1)
                putString(ARG_BOOK_TEXT, bookText)
            }
            return fragment
        }
    }
}
