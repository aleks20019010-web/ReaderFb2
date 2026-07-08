package com.nightread.app.data

class SyncCacheManager(private val dao: CloudFileDao) {

    suspend fun save(sha1: String, fileName: String, lastModified: String, size: Long = 0L) {
        val entity = CloudFileEntity(
            path = fileName,
            sha1 = sha1,
            size = size,
            lastModified = lastModified
        )
        dao.insert(entity)
    }

    suspend fun getAllSha1s(): Set<String> {
        return dao.getAll().map { it.sha1 }.toSet()
    }

    suspend fun getByPath(path: String): CloudFileEntity? {
        return dao.getByPath(path)
    }

    suspend fun deleteByPath(path: String) {
        dao.deleteByPath(path)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun getAllEntries(): List<CloudFileEntity> {
        return dao.getAll()
    }
}
