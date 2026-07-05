with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "r") as f:
    content = f.read()

import re

# Insert declarations
content = content.replace("private lateinit var cardSync: View", "private lateinit var cardSync: View\n    private lateinit var txtSyncFolder: TextView\n    private lateinit var btnSelectFolder: Button")

# Insert bindings
bindings = """        btnSyncNow = view.findViewById(R.id.btnSyncNow)
        txtSyncFolder = view.findViewById(R.id.txtSyncFolder)
        btnSelectFolder = view.findViewById(R.id.btnSelectFolder)"""
content = content.replace("        btnSyncNow = view.findViewById(R.id.btnSyncNow)", bindings)

# Update txtSyncFolder when UI updates
update_ui = """        val authorized = AuthManager.isAuthorized(context)
        if (authorized) {
            val syncFolder = YandexDiskManager.getSyncFolder(context)
            txtSyncFolder.text = syncFolder.removePrefix("disk:")
"""
content = content.replace("        val authorized = AuthManager.isAuthorized(context)\n        if (authorized) {", update_ui)

# Add listener
listener = """        btnSelectFolder.setOnClickListener {
            showFolderSelectionDialog()
        }
        btnSyncNow.setOnClickListener {"""
content = content.replace("        btnSyncNow.setOnClickListener {", listener)

# Add showFolderSelectionDialog function
dialog_func = """    private fun showFolderSelectionDialog() {
        val context = requireContext()
        lifecycleScope.launch {
            val pd = android.app.ProgressDialog(context)
            pd.setMessage("Загрузка папок...")
            pd.setCancelable(false)
            pd.show()
            
            val folders = YandexDiskManager.getFolders(context, "disk:/")
            pd.dismiss()
            
            val folderNames = folders.map { it.name }.toMutableList()
            folderNames.add(0, "/") // Root folder
            
            val builder = android.app.AlertDialog.Builder(context)
            builder.setTitle("Выберите папку")
            builder.setItems(folderNames.toTypedArray()) { _, which ->
                val selectedPath = if (which == 0) "disk:/" else folders[which - 1].path
                YandexDiskManager.setSyncFolder(context, selectedPath)
                updateUi()
            }
            builder.setNegativeButton("Отмена", null)
            builder.show()
        }
    }

    private fun showStatsAndSync() {"""
content = content.replace("    private fun showStatsAndSync() {", dialog_func)

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "w") as f:
    f.write(content)
