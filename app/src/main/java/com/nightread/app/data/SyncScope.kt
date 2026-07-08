package com.nightread.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Глобальный контекст выполнения для фоновых задач синхронизации.
 * Использует SupervisorJob, чтобы ошибка в одной задаче не отменяла весь Scope.
 */
object SyncScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
