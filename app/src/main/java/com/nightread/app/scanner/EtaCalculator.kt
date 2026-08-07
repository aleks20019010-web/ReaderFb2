package com.nightread.app.scanner

class EtaCalculator {
    private var startTime = 0L
    private var lastProgress = 0
    private var lastTime = 0L
    private var emaSpeed = 0.0
    private val smoothingFactor = 0.3
    
    fun calculate(processed: Int, total: Int): String {
        if (processed <= 0 || total <= 0 || processed >= total) {
            return if (processed >= total) "Завершается..." else "Вычисляется..."
        }
        
        val now = System.currentTimeMillis()
        
        if (lastProgress == 0) {
            startTime = now
            lastProgress = processed
            lastTime = now
            return "Вычисляется..."
        }
        
        val timeDiff = (now - lastTime) / 1000.0
        if (timeDiff < 1.0) return "Вычисляется..."
        
        val instantSpeed = (processed - lastProgress) / timeDiff
        
        if (instantSpeed > 0) {
            if (emaSpeed == 0.0) {
                emaSpeed = instantSpeed
            } else {
                emaSpeed = smoothingFactor * instantSpeed + (1 - smoothingFactor) * emaSpeed
            }
        }
        
        val remaining = total - processed
        val etaSeconds = if (emaSpeed > 0) (remaining / emaSpeed).toLong() else 0L
        
        lastProgress = processed
        lastTime = now
        
        return when {
            etaSeconds <= 0 -> "Вычисляется..."
            etaSeconds < 60 -> "~${etaSeconds} сек"
            etaSeconds < 3600 -> "~${etaSeconds / 60} мин"
            else -> {
                val hours = etaSeconds / 3600
                val minutes = (etaSeconds % 3600) / 60
                "~${hours}ч ${minutes}м"
            }
        }
    }
    
    fun getSpeed(): String {
        return if (emaSpeed > 0) {
            val booksPerMin = emaSpeed * 60
            if (booksPerMin > 1) {
                "${String.format("%.1f", booksPerMin)} книг/мин"
            } else {
                "${String.format("%.1f", emaSpeed)} книг/сек"
            }
        } else {
            "Вычисляется..."
        }
    }
    
    fun reset() {
        startTime = 0L
        lastProgress = 0
        lastTime = 0L
        emaSpeed = 0.0
    }
}
