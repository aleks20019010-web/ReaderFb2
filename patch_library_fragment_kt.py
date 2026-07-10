import sys

file_path = 'app/src/main/java/com/nightread/app/ui/LibraryFragment.kt'
with open(file_path, 'r') as f:
    content = f.read()

import_lines = "import kotlinx.coroutines.withContext\nimport com.nightread.app.data.AppDatabase\nimport android.os.Handler\nimport android.os.Looper\n"
if "import android.os.Handler" not in content:
    content = content.replace("import android.os.Bundle", import_lines + "import android.os.Bundle")

banner_vars = """    private lateinit var layoutNewBooksBanner: LinearLayout
    private lateinit var tvNewBooksCount: TextView
    private lateinit var btnShowNewBooks: TextView
    private lateinit var btnCloseNewBooks: ImageView
    private val hideBannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable { layoutNewBooksBanner.visibility = View.GONE }
"""
content = content.replace("    private lateinit var tvEmptyStateDesc: TextView", banner_vars + "    private lateinit var tvEmptyStateDesc: TextView")

banner_init = """        layoutNewBooksBanner = view.findViewById(R.id.layoutNewBooksBanner)
        tvNewBooksCount = view.findViewById(R.id.tvNewBooksCount)
        btnShowNewBooks = view.findViewById(R.id.btnShowNewBooks)
        btnCloseNewBooks = view.findViewById(R.id.btnCloseNewBooks)

        btnShowNewBooks.setOnClickListener {
            layoutNewBooksBanner.visibility = View.GONE
            startActivity(android.content.Intent(requireContext(), NewBooksActivity::class.java))
        }

        btnCloseNewBooks.setOnClickListener {
            layoutNewBooksBanner.visibility = View.GONE
            hideBannerHandler.removeCallbacks(hideBannerRunnable)
        }
"""
content = content.replace("        tvEmptyStateDesc = view.findViewById(R.id.tvEmptyStateDesc)", banner_init + "        tvEmptyStateDesc = view.findViewById(R.id.tvEmptyStateDesc)")

banner_logic = """
            if (wasScanning) {
                wasScanning = false
                if (state.status.isNotBlank()) {
                    context?.let { ctx ->
                        CustomToast.show(ctx, state.status)
                    }
                }
                // Check new books count
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(requireContext())
                    val newBooks = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        db.bookDao().getNewBooks()
                    }
                    if (newBooks.isNotEmpty()) {
                        layoutNewBooksBanner.visibility = View.VISIBLE
                        tvNewBooksCount.text = "Найдено новых книг: ${newBooks.size}"
                        hideBannerHandler.removeCallbacks(hideBannerRunnable)
                        hideBannerHandler.postDelayed(hideBannerRunnable, 10000)
                    } else {
                        layoutNewBooksBanner.visibility = View.GONE
                    }
                }
            }
"""
content = content.replace("""
            if (wasScanning) {
                wasScanning = false
                if (state.status.isNotBlank()) {
                    context?.let { ctx ->
                        CustomToast.show(ctx, state.status)
                    }
                }
            }""", banner_logic)

with open(file_path, 'w') as f:
    f.write(content)
