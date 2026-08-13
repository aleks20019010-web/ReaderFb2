package com.nightread.app.scanner

class MemoryMonitor {
    private val MEMORY_LOW_THRESHOLD = 0.15f
    
    fun getMemoryStatus(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val freeMemory = runtime.freeMemory()
        
        val maxMB = maxMemory / (1024 * 1024)
        val usedMB = usedMemory / (1024 * 1024)
        val freeMB = freeMemory / (1024 * 1024)
        
        return "$usedMB MB / $maxMB MB (свободно $freeMB MB)"
    }
    
    fun isMemoryLow(): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val availableMemory = runtime.maxMemory() - usedMemory
        return availableMemory < runtime.maxMemory() * MEMORY_LOW_THRESHOLD
    }
    
    fun getFreeMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return (runtime.maxMemory() - usedMemory) / (1024 * 1024)
    }
    
    fun getUsedMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
    
    fun getMaxMemoryMB(): Long {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024)
    }
}
