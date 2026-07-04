package com.example.data

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

data class SyncBook(
    val title: String,
    val author: String,
    val currentProgressChar: Int,
    val lastReadTime: Long
)

data class SyncNote(
    val bookTitle: String,
    val selectedText: String,
    val noteText: String,
    val charOffset: Int,
    val timestamp: Long
)

data class SyncPayload(
    val books: List<SyncBook>,
    val notes: List<SyncNote>
)

class SyncManager(private val context: Context) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(SyncPayload::class.java)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false

    /**
     * Gets the local Wi-Fi IP address of this device
     */
    fun getLocalIpAddress(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            Formatter.formatIpAddress(ipAddress)
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    /**
     * Exports the current local database state into a JSON SyncPayload
     */
    suspend fun getLocalPayload(repository: BookRepository): SyncPayload = withContext(Dispatchers.IO) {
        val books = repository.allBooks.first().map {
            SyncBook(it.title, it.author ?: "Неизвестен", it.currentProgressChar, it.lastReadTime)
        }
        val notes = repository.allNotes.first().map {
            SyncNote(it.bookTitle, it.selectedText, it.noteText, it.charOffset, it.timestamp)
        }
        SyncPayload(books, notes)
    }

    /**
     * Merges a foreign SyncPayload into the local Room database using lastReadTime/timestamps
     */
    suspend fun mergePayload(repository: BookRepository, payload: SyncPayload): Unit = withContext(Dispatchers.IO) {
        val localBooks = repository.allBooks.first()

        // Merge books
        payload.books.forEach { incoming ->
            val matchingLocal = localBooks.find { it.title.equals(incoming.title, ignoreCase = true) }
            if (matchingLocal != null) {
                // If incoming progress is newer, update it
                if (incoming.lastReadTime > matchingLocal.lastReadTime) {
                    repository.updateBook(matchingLocal.copy(
                        currentProgressChar = incoming.currentProgressChar,
                        lastReadTime = incoming.lastReadTime
                    ))
                }
            } else {
                // If book doesn't exist locally, insert it with a placeholder content or download
                repository.insertBook(BookEntity(
                    title = incoming.title,
                    author = incoming.author,
                    content = "Синхронизированная книга. Содержимое отсутствует, так как книга была импортирована через синхронизацию.",
                    currentProgressChar = incoming.currentProgressChar,
                    lastReadTime = incoming.lastReadTime,
                    totalCharacters = 1000
                ))
            }
        }

        // Merge notes
        val localNotes = repository.allNotes.first()
        payload.notes.forEach { incoming ->
            // Check if exact note exists based on bookTitle, charOffset and noteText/timestamp
            val exists = localNotes.any {
                it.bookTitle.equals(incoming.bookTitle, ignoreCase = true) &&
                        it.charOffset == incoming.charOffset &&
                        it.noteText == incoming.noteText
            }
            if (!exists) {
                // Find matching local book to tie note to, otherwise use default ID
                val matchingBook = repository.allBooks.first().find { it.title.equals(incoming.bookTitle, ignoreCase = true) }
                repository.insertNote(NoteEntity(
                    bookId = matchingBook?.id ?: 0,
                    bookTitle = incoming.bookTitle,
                    selectedText = incoming.selectedText,
                    noteText = incoming.noteText,
                    charOffset = incoming.charOffset,
                    timestamp = incoming.timestamp
                ))
            }
        }
    }

    /**
     * Starts a simple, lightweight raw Socket server to host local Wi-Fi sync
     */
    suspend fun startSyncServer(repository: BookRepository, port: Int = 8080, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            if (isServerRunning) {
                onLog("Сервер уже запущен на порту $port")
                return@withContext
            }
            serverSocket = ServerSocket(port)
            isServerRunning = true
            onLog("Сервер синхронизации запущен на ${getLocalIpAddress()}:$port")

            while (isServerRunning) {
                val socket = serverSocket?.accept() ?: break
                onLog("Подключение от: ${socket.inetAddress.hostAddress}")

                // Read request headers
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String? = reader.readLine()
                if (line == null) {
                    socket.close()
                    continue
                }

                val tokens = line.split(" ")
                val method = tokens.getOrNull(0) ?: "GET"
                val path = tokens.getOrNull(1) ?: "/"

                // Simple HTTP route handling
                if (method == "GET" && path.startsWith("/sync")) {
                    val localPayload = getLocalPayload(repository)
                    val jsonBytes = payloadAdapter.toJson(localPayload).toByteArray()
                    sendHttpResponse(socket.getOutputStream(), 200, "application/json", jsonBytes)
                    onLog("Данные успешно отправлены подключенному устройству.")
                } else if (method == "POST" && path.startsWith("/sync")) {
                    // Read content length
                    var contentLength = 0
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.isEmpty()) break
                        if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }

                    // Read POST body
                    val bodyChars = CharArray(contentLength)
                    var charsRead = 0
                    while (charsRead < contentLength) {
                        val result = reader.read(bodyChars, charsRead, contentLength - charsRead)
                        if (result == -1) break
                        charsRead += result
                    }
                    val body = String(bodyChars)

                    val incomingPayload = payloadAdapter.fromJson(body)
                    if (incomingPayload != null) {
                        mergePayload(repository, incomingPayload)
                        onLog("Данные успешно получены и объединены.")

                        // Return fully updated combined state back to client
                        val mergedPayload = getLocalPayload(repository)
                        val responseBytes = payloadAdapter.toJson(mergedPayload).toByteArray()
                        sendHttpResponse(socket.getOutputStream(), 200, "application/json", responseBytes)
                    } else {
                        sendHttpResponse(socket.getOutputStream(), 400, "text/plain", "Invalid JSON payload".toByteArray())
                        onLog("Ошибка: получен неверный формат данных.")
                    }
                } else {
                    sendHttpResponse(socket.getOutputStream(), 404, "text/plain", "Not Found".toByteArray())
                }
                socket.close()
            }
        } catch (e: Exception) {
            onLog("Ошибка сервера: ${e.localizedMessage}")
        } finally {
            isServerRunning = false
            serverSocket = null
        }
    }

    private fun sendHttpResponse(outputStream: OutputStream, statusCode: Int, contentType: String, body: ByteArray) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType; charset=UTF-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        outputStream.write(header.toByteArray())
        outputStream.write(body)
        outputStream.flush()
    }

    /**
     * Stops the running Wi-Fi sync server
     */
    fun stopSyncServer() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("SyncManager", "Error closing server socket", e)
        }
        serverSocket = null
    }

    /**
     * Performs direct synchronization as a client with a remote sync server device
     */
    suspend fun clientSync(repository: BookRepository, ipAddress: String, port: Int = 8080, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        onLog("Подключение к $ipAddress:$port для синхронизации...")
        try {
            // Step 1: GET server payload and merge it
            val getRequest = Request.Builder()
                .url("http://$ipAddress:$port/sync")
                .get()
                .build()

            val getResponse = httpClient.newCall(getRequest).execute()
            if (!getResponse.isSuccessful) {
                onLog("Не удалось скачать данные с сервера: Код ${getResponse.code}")
                return@withContext false
            }

            val serverJson = getResponse.body?.string() ?: ""
            val serverPayload = payloadAdapter.fromJson(serverJson)
            if (serverPayload == null) {
                onLog("Ошибка: Сервер вернул пустые или поврежденные данные.")
                return@withContext false
            }

            // Merge server data into local DB
            mergePayload(repository, serverPayload)
            onLog("Успешно объединены данные с удаленного устройства.")

            // Step 2: POST local merged state back to the server
            val combinedPayload = getLocalPayload(repository)
            val requestBody = payloadAdapter.toJson(combinedPayload).toRequestBody("application/json".toMediaType())
            val postRequest = Request.Builder()
                .url("http://$ipAddress:$port/sync")
                .post(requestBody)
                .build()

            val postResponse = httpClient.newCall(postRequest).execute()
            if (!postResponse.isSuccessful) {
                onLog("Второй этап: Не удалось отправить объединенные данные обратно.")
                return@withContext false
            }

            // Sync successfully complete! Read final merged body just in case
            val finalJson = postResponse.body?.string() ?: ""
            val finalPayload = payloadAdapter.fromJson(finalJson)
            if (finalPayload != null) {
                mergePayload(repository, finalPayload)
            }

            onLog("Устройство полностью синхронизировано!")
            return@withContext true
        } catch (e: Exception) {
            onLog("Ошибка синхронизации: ${e.localizedMessage}")
            return@withContext false
        }
    }

    /**
     * Sync with a remote cloud API/endpoint configured by the user
     */
    suspend fun syncWithCloud(repository: BookRepository, url: String, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        onLog("Синхронизация с облаком: $url")
        try {
            val localPayload = getLocalPayload(repository)
            val requestBody = payloadAdapter.toJson(localPayload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseJson = response.body?.string() ?: ""
                val remotePayload = payloadAdapter.fromJson(responseJson)
                if (remotePayload != null) {
                    mergePayload(repository, remotePayload)
                    onLog("Облачная синхронизация завершена успешно!")
                    return@withContext true
                }
            }
            onLog("Ошибка облака: Код ${response.code}")
            return@withContext false
        } catch (e: Exception) {
            onLog("Ошибка облака: ${e.localizedMessage}")
            return@withContext false
        }
    }

    /**
     * Exports DB as a clean shareable JSON text
     */
    suspend fun exportToJson(repository: BookRepository): String {
        val payload = getLocalPayload(repository)
        return payloadAdapter.indent("  ").toJson(payload)
    }

    /**
     * Imports DB from standard JSON text
     */
    suspend fun importFromJson(repository: BookRepository, json: String): Boolean {
        return try {
            val payload = payloadAdapter.fromJson(json)
            if (payload != null) {
                mergePayload(repository, payload)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
