package com.nightread.app.data

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DatabaseExecutors {
    
    private var isShutdown = false

    // Для выполнения запросов (чтение)
    val queryExecutor: ExecutorService = Executors.newFixedThreadPool(4)
    
    // Для выполнения транзакций (запись)
    val transactionExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    // Для фоновых операций (бэкап, импорт)
    val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    fun shutdown() {
        synchronized(this) {
            if (isShutdown) return
            isShutdown = true
        }

        listOf(queryExecutor, transactionExecutor, backgroundExecutor).forEach { executor ->
            executor.shutdown()
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }
}
