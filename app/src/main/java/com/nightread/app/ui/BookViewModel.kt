    fun startLocalBookScan(rootPath: String = "/storage/emulated/0") {
        if (isScanning) {
            Log.d("BookViewModel", "Scan already in progress")
            return
        }
        
        try {
            val context = try {
                getApplication<Application>().applicationContext
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get application context", e)
                isScanning = false
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            val db = try {
                database ?: AppDatabase.getDatabase(context)
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get database", e)
                isScanning = false
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            val dao = try {
                db.bookDao()
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to obtain BookDao", e)
                isScanning = false
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            isScanning = true
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = true
            
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val scanner = com.nightread.app.scanner.LibraryScanner.getInstance(context, dao)
                    scanner.scanBooks().join()
                    
                    Log.d("BookViewModel", "Book scan completed successfully")
                } catch (e: OutOfMemoryError) {
                    Log.e("BookViewModel", "Out of memory during scan", e)
                    System.gc()
                    withContext(Dispatchers.Main) {
                        try {
                            android.widget.Toast.makeText(
                                context,
                                "Недостаточно памяти для сканирования",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (ignored: Exception) {}
                    }
                } catch (e: CancellationException) {
                    Log.d("BookViewModel", "Book scan cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e("BookViewModel", "Error during book scan", e)
                    withContext(Dispatchers.Main) {
                        try {
                            android.widget.Toast.makeText(
                                context,
                                "Ошибка при сканировании: ${e.localizedMessage ?: "неизвестная ошибка"}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (ignored: Exception) {}
                    }
                } finally {
                    isScanning = false
                    com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                }
            }
        } catch (e: Exception) {
            Log.e("BookViewModel", "Unexpected error in startLocalBookScan", e)
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
        }
    }

    fun startIncrementalBookScan() {
        if (isScanning) {
            Log.d("BookViewModel", "Incremental scan already in progress")
            return
        }
        
        try {
            val context = try {
                getApplication<Application>().applicationContext
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get application context", e)
                return
            }
            
            val db = try {
                database ?: AppDatabase.getDatabase(context)
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get database", e)
                return
            }
            
            val dao = try {
                db.bookDao()
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to obtain BookDao", e)
                return
            }
            
            isScanning = true
            
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val scanner = com.nightread.app.scanner.LibraryScanner.getInstance(context, dao)
                    scanner.scanBooks(force = false).join()
                    
                    Log.d("BookViewModel", "Incremental book scan completed")
                } catch (e: OutOfMemoryError) {
                    Log.e("BookViewModel", "Out of memory during incremental scan", e)
                    System.gc()
                } catch (e: CancellationException) {
                    Log.d("BookViewModel", "Incremental scan cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e("BookViewModel", "Error during incremental scan", e)
                } finally {
                    isScanning = false
                }
            }
        } catch (e: Exception) {
            Log.e("BookViewModel", "Unexpected error in startIncrementalBookScan", e)
            isScanning = false
        }
    }
