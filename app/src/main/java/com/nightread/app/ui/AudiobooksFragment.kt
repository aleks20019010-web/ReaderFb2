package com.nightread.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookEntity
import com.nightread.app.service.AudiobookPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudiobooksFragment : Fragment() {

    private lateinit var rvAudiobooks: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var layoutMiniPlayer: View
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniAuthor: TextView
    private lateinit var btnMiniPlayPause: ImageButton
    private lateinit var pbMiniPlayer: ProgressBar
    private lateinit var searchView: SearchView
    private lateinit var btnScan: MaterialButton
    private lateinit var btnScanEmpty: MaterialButton

    private lateinit var adapter: AudiobookAdapter
    private var allAudiobooks: List<BookEntity> = emptyList()

    private val audioStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudiobookPlaybackService.BROADCAST_AUDIOBOOK_STATUS) {
                val isPlaying = intent.getBooleanExtra(AudiobookPlaybackService.EXTRA_IS_PLAYING, false)
                val pos = intent.getIntExtra(AudiobookPlaybackService.EXTRA_CURRENT_POSITION, 0)
                val duration = intent.getIntExtra(AudiobookPlaybackService.EXTRA_DURATION, 0)

                updateMiniPlayerUI(isPlaying, pos, duration)
            }
        }
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            importAudioUris(uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audiobooks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvAudiobooks = view.findViewById(R.id.rvAudiobooks)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyAudiobooks)
        layoutMiniPlayer = view.findViewById(R.id.layoutMiniPlayer)
        tvMiniTitle = view.findViewById(R.id.tvMiniTitle)
        tvMiniAuthor = view.findViewById(R.id.tvMiniAuthor)
        btnMiniPlayPause = view.findViewById(R.id.btnMiniPlayPause)
        pbMiniPlayer = view.findViewById(R.id.pbMiniPlayer)
        searchView = view.findViewById(R.id.searchAudiobooks)
        btnScan = view.findViewById(R.id.btnScanAudio)
        btnScanEmpty = view.findViewById(R.id.btnScanEmptyState)

        adapter = AudiobookAdapter(emptyList()) { book ->
            val path = book.filePath ?: return@AudiobookAdapter
            val player = AudioPlayerBottomSheet.newInstance(path, book.title, book.author ?: "Audiobook", book.sha1)
            player.show(parentFragmentManager, "AudioPlayerBottomSheet")
        }

        rvAudiobooks.layoutManager = LinearLayoutManager(requireContext())
        rvAudiobooks.adapter = adapter

        btnScan.setOnClickListener { scanDeviceForAudiobooks() }
        btnScanEmpty.setOnClickListener { scanDeviceForAudiobooks() }

        layoutMiniPlayer.setOnClickListener {
            val currentPath = AudiobookPlaybackService.currentFilePath ?: return@setOnClickListener
            val book = allAudiobooks.find { it.filePath == currentPath }
            val title = book?.title ?: tvMiniTitle.text.toString()
            val author = book?.author ?: tvMiniAuthor.text.toString()
            val sha1 = book?.sha1 ?: ""

            val player = AudioPlayerBottomSheet.newInstance(currentPath, title, author, sha1)
            player.show(parentFragmentManager, "AudioPlayerBottomSheet")
        }

        btnMiniPlayPause.setOnClickListener {
            val currentPath = AudiobookPlaybackService.currentFilePath ?: return@setOnClickListener
            val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                action = if (AudiobookPlaybackService.isPlayingAudiobook) {
                    AudiobookPlaybackService.ACTION_PAUSE
                } else {
                    AudiobookPlaybackService.ACTION_PLAY
                }
                putExtra(AudiobookPlaybackService.EXTRA_FILE_PATH, currentPath)
            }
            requireContext().startService(intent)
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterAudiobooks(newText ?: "")
                return true
            }
        })

        loadAudiobooksFromDb()
    }

    private fun loadAudiobooksFromDb() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val books = db.bookDao().getAllBooksSync()

            val audioList = books.filter { isAudioFile(it.filePath, it.fileSize) }

            withContext(Dispatchers.Main) {
                allAudiobooks = audioList
                updateListUI(audioList)
            }
        }
    }

    private fun updateListUI(list: List<BookEntity>) {
        if (list.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvAudiobooks.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvAudiobooks.visibility = View.VISIBLE
            adapter.updateData(list)
        }
    }

    private fun filterAudiobooks(query: String) {
        if (query.isBlank()) {
            updateListUI(allAudiobooks)
        } else {
            val filtered = allAudiobooks.filter {
                it.title.contains(query, ignoreCase = true) ||
                (it.author ?: "").contains(query, ignoreCase = true)
            }
            updateListUI(filtered)
        }
    }

    private fun scanDeviceForAudiobooks() {
        CustomToast.show(requireContext(), "Сканирование аудиокниг...")
        lifecycleScope.launch(Dispatchers.IO) {
            val discovered = mutableListOf<BookEntity>()
            val dirsToScan = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStorageDirectory(), "Audiobooks"),
                File(Environment.getExternalStorageDirectory(), "Books")
            )

            for (dir in dirsToScan) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { file ->
                        if (file.isFile && isAudioFile(file.absolutePath, file.length())) {
                            val (title, author) = extractAudioMetadata(file.absolutePath)
                            val entity = BookEntity(
                                sha1 = getFileSha1(file),
                                title = title,
                                author = author,
                                category = "Audiobook",
                                filePath = file.absolutePath,
                                fileSize = file.length(),
                                dateAdded = System.currentTimeMillis()
                            )
                            discovered.add(entity)
                        }
                    }
                }
            }

            val db = AppDatabase.getDatabase(requireContext())
            for (audio in discovered) {
                db.bookDao().insertBook(audio)
            }

            val allAudio = db.bookDao().getAllBooksSync().filter { isAudioFile(it.filePath, it.fileSize) }

            withContext(Dispatchers.Main) {
                allAudiobooks = allAudio
                updateListUI(allAudio)
                CustomToast.show(requireContext(), "Найдено аудиокниг: ${allAudio.size}")
            }
        }
    }

    private fun importAudioUris(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            for (uri in uris) {
                try {
                    val context = requireContext()
                    val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "audio_${System.currentTimeMillis()}.mp3"
                    val destFile = File(context.getExternalFilesDir("Audiobooks") ?: context.filesDir, fileName)

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (!isAudioFile(destFile.absolutePath, destFile.length())) {
                        destFile.delete()
                        continue
                    }

                    val (title, author) = extractAudioMetadata(destFile.absolutePath)
                    val entity = BookEntity(
                        sha1 = getFileSha1(destFile),
                        title = title,
                        author = author,
                        category = "Audiobook",
                        filePath = destFile.absolutePath,
                        fileSize = destFile.length(),
                        dateAdded = System.currentTimeMillis()
                    )
                    db.bookDao().insertBook(entity)
                } catch (e: Exception) {
                    // Ignore import errors for broken files
                }
            }

            val allAudio = db.bookDao().getAllBooksSync().filter { isAudioFile(it.filePath, it.fileSize) }
            withContext(Dispatchers.Main) {
                allAudiobooks = allAudio
                updateListUI(allAudio)
            }
        }
    }

    private fun extractAudioMetadata(path: String): Pair<String, String> {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(path)
            val t = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            val a = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            retriever.release()
            val file = File(path)
            val title = if (!t.isNullOrBlank()) t else file.nameWithoutExtension
            val author = if (!a.isNullOrBlank()) a else "Аудиокнига"
            Pair(title, author)
        } catch (e: Exception) {
            val file = File(path)
            Pair(file.nameWithoutExtension, "Аудиокнига")
        }
    }

    private fun getFileSha1(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            val bytes = file.readBytes()
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            file.absolutePath.hashCode().toString()
        }
    }

    private fun isAudioFile(path: String?, sizeInBytes: Long? = null): Boolean {
        if (path.isNullOrEmpty()) return false
        val lower = path.lowercase()
        val isM4b = lower.endsWith(".m4b")
        val isMp3OrM4a = lower.endsWith(".mp3") || lower.endsWith(".m4a")
        val isOtherAudio = lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.endsWith(".flac")
        
        if (!isM4b && !isMp3OrM4a && !isOtherAudio) return false

        // 1. Проверять расширение файла: если .m4b — сразу считать аудиокнигой.
        if (isM4b) return true

        val size = sizeInBytes ?: try {
            File(path).length()
        } catch (e: Exception) {
            0L
        }

        if (isMp3OrM4a) {
            // 3. Если размер файла > 50 МБ — считать аудиокнигой.
            if (size > 50 * 1024 * 1024) {
                return true
            }
            // 4. Если размер файла < 10 МБ — считать обычной песней (и не показывать в библиотеке).
            if (size < 10 * 1024 * 1024) {
                return false
            }
            return true
        }

        return size >= 10 * 1024 * 1024
    }

    private fun updateMiniPlayerUI(isPlaying: Boolean, position: Int, duration: Int) {
        val currentPath = AudiobookPlaybackService.currentFilePath
        if (currentPath == null) {
            layoutMiniPlayer.visibility = View.GONE
            return
        }

        layoutMiniPlayer.visibility = View.VISIBLE

        val book = allAudiobooks.find { it.filePath == currentPath }
        tvMiniTitle.text = book?.title ?: File(currentPath).nameWithoutExtension
        tvMiniAuthor.text = book?.author ?: "Аудиокнига"

        btnMiniPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_media_pause_custom else R.drawable.ic_media_play_custom
        )

        if (duration > 0) {
            pbMiniPlayer.max = duration
            pbMiniPlayer.progress = position
        }
    }

    override fun onResume() {
        super.onResume()
        loadAudiobooksFromDb()
        val filter = IntentFilter(AudiobookPlaybackService.BROADCAST_AUDIOBOOK_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(audioStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(audioStatusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(audioStatusReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
