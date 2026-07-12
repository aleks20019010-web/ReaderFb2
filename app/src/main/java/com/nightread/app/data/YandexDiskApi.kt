package com.nightread.app.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class UserInfo(
    @Json(name = "login") val login: String,
    @Json(name = "display_name") val displayName: String? = null
)

@JsonClass(generateAdapter = true)
data class DiskInfoResponse(
    @Json(name = "total_space") val totalSpace: Long,
    @Json(name = "used_space") val usedSpace: Long,
    @Json(name = "user") val user: UserInfo? = null
)

@JsonClass(generateAdapter = true)
data class ResourceResponse(
    @Json(name = "type") val type: String,
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "modified") val modified: String? = null,
    @Json(name = "_embedded") val embedded: EmbeddedResources? = null
)

@JsonClass(generateAdapter = true)
data class EmbeddedResources(
    @Json(name = "items") val items: List<ResourceItem>,
    @Json(name = "limit") val limit: Int? = null,
    @Json(name = "offset") val offset: Int? = null,
    @Json(name = "total") val total: Int? = null
)

@JsonClass(generateAdapter = true)
data class ResourceItem(
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String, // "dir" or "file"
    @Json(name = "path") val path: String,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "md5") val md5: String? = null,
    @Json(name = "sha256") val sha256: String? = null,
    @Json(name = "modified") val modified: String? = null
)

@JsonClass(generateAdapter = true)
data class LinkResponse(
    @Json(name = "href") val href: String,
    @Json(name = "method") val method: String,
    @Json(name = "templated") val templated: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class BookProgressPayload(
    @Json(name = "sha1") val sha1: String,
    @Json(name = "page") val page: Int,
    @Json(name = "charOffset") val charOffset: Int,
    @Json(name = "progress") val progress: Int,
    @Json(name = "lastReadTime") val lastReadTime: Long,
    @Json(name = "totalChars") val totalChars: Int
)

interface YandexDiskApi {

    @GET("v1/disk")
    suspend fun getDiskInfo(
        @Header("Authorization") token: String
    ): DiskInfoResponse

    @GET("v1/disk/resources")
    suspend fun getResource(
        @Header("Authorization") token: String,
        @Query("path") path: String,
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String? = null
    ): ResourceResponse

    @PUT("v1/disk/resources")
    suspend fun createDirectory(
        @Header("Authorization") token: String,
        @Query("path") path: String
    ): Response<Unit>

    @GET("v1/disk/resources/upload")
    suspend fun getUploadLink(
        @Header("Authorization") token: String,
        @Query("path") path: String,
        @Query("overwrite") overwrite: Boolean = true
    ): LinkResponse

    @GET("v1/disk/resources/download")
    suspend fun getDownloadLink(
        @Header("Authorization") token: String,
        @Query("path") path: String
    ): LinkResponse

    @PUT
    suspend fun uploadFile(
        @Url url: String,
        @Body file: RequestBody
    ): Response<Unit>

    @GET
    @Streaming
    suspend fun downloadFile(
        @Url url: String
    ): ResponseBody

    @DELETE("v1/disk/resources")
    suspend fun deleteResource(
        @Header("Authorization") token: String,
        @Query("path") path: String,
        @Query("permanently") permanently: Boolean = true
    ): Response<Unit>
}
