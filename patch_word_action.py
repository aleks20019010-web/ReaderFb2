import re

file_path = "app/src/main/java/com/nightread/app/ui/WordActionBottomSheet.kt"
with open(file_path, "r") as f:
    content = f.read()

replacement = """
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSelectedWord = view.findViewById<TextView>(R.id.tvSelectedWord)
        tvSelectedWord.text = word

        val layoutAiResponse = view.findViewById<View>(R.id.layoutAiResponse)
        val pbAiLoading = view.findViewById<ProgressBar>(R.id.pbAiLoading)
        val tvAiResponse = view.findViewById<TextView>(R.id.tvAiResponse)

        view.findViewById<View>(R.id.btnFind).setOnClickListener {
            val bookReaderActivity = activity as? BookReaderActivity
            if (bookReaderActivity != null) {
                bookReaderActivity.performSmartSearch(word)
            } else {
                CustomToast.show(requireContext(), "Функция доступна только на экране чтения", Toast.LENGTH_SHORT)
            }
            dismiss()
        }

        view.findViewById<View>(R.id.btnCopy).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Word", word)
            clipboard.setPrimaryClip(clip)
            CustomToast.show(requireContext(), "Текст скопирован", Toast.LENGTH_SHORT)
            dismiss()
        }

        // AI Buttons
        view.findViewById<View>(R.id.btnAiExplain)?.setOnClickListener {
            queryLocalAi("explain", layoutAiResponse, pbAiLoading, tvAiResponse)
        }
        
        view.findViewById<View>(R.id.btnAiTranslate)?.setOnClickListener {
            queryLocalAi("translate", layoutAiResponse, pbAiLoading, tvAiResponse)
        }
        
        view.findViewById<View>(R.id.btnAiSummarize)?.setOnClickListener {
            queryLocalAi("summarize", layoutAiResponse, pbAiLoading, tvAiResponse)
        }
        
        view.findViewById<View>(R.id.btnAiCharacter)?.setOnClickListener {
            queryLocalAi("character", layoutAiResponse, pbAiLoading, tvAiResponse)
        }
        
        view.findViewById<View>(R.id.btnAiSimplify)?.setOnClickListener {
            queryLocalAi("simplify", layoutAiResponse, pbAiLoading, tvAiResponse)
        }

        val btnCreateNote = view.findViewById<View>(R.id.btnCreateNote)
        val layoutNoteInput = view.findViewById<View>(R.id.layoutNoteInput)
        val etNoteText = view.findViewById<android.widget.EditText>(R.id.etNoteText)
        val btnSaveNote = view.findViewById<View>(R.id.btnSaveNote)

        btnCreateNote.setOnClickListener {
            layoutNoteInput.visibility = View.VISIBLE
            layoutAiResponse.visibility = View.GONE
            etNoteText.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(etNoteText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        btnSaveNote.setOnClickListener {
            val noteText = etNoteText.text.toString().trim()
            if (noteText.isEmpty()) {
                CustomToast.show(requireContext(), "Пожалуйста, введите текст заметки", Toast.LENGTH_SHORT)
                return@setOnClickListener
            }
            val bookReaderActivity = activity as? BookReaderActivity
            if (bookReaderActivity != null) {
                bookReaderActivity.saveNoteForBook(word, noteText)
                CustomToast.show(requireContext(), "Заметка успешно сохранена", Toast.LENGTH_SHORT)
                dismiss()
            } else {
                CustomToast.show(requireContext(), "Ошибка: экран чтения недоступен", Toast.LENGTH_SHORT)
            }
        }
    }

    private fun queryLocalAi(
        actionType: String,
        layoutAiResponse: View,
        pbAiLoading: ProgressBar,
        tvAiResponse: TextView
    ) {
        layoutAiResponse.visibility = View.VISIBLE
        pbAiLoading.visibility = View.VISIBLE
        tvAiResponse.text = "Локальный ИИ анализирует..."

        val ctx = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.delay(200)
                val textResponse = withContext(Dispatchers.IO) {
                    com.nightread.app.data.LocalAiEngine.customAiPrompt(ctx, word, contextSnippet, actionType)
                }

                if (isAdded) {
                    pbAiLoading.visibility = View.GONE
                    val accentColor = Color.parseColor("#9B59B6")
                    tvAiResponse.text = MarkdownRenderer.render(requireContext(), textResponse, accentColor)
                }
            } catch (e: Exception) {
                if (isAdded) {
                    pbAiLoading.visibility = View.GONE
                    tvAiResponse.text = "Ошибка анализа: ${e.localizedMessage}"
                }
            }
        }
    }
"""

start_idx = content.find("override fun onViewCreated")
end_idx = content.find("companion object {")

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + replacement.strip() + "\n\n    " + content[end_idx:]
    with open(file_path, "w") as f:
        f.write(content)
