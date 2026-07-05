package com.example.data

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
    @Json(name = "_embedded") val embedded: EmbeddedResources? = null
)

@JsonClass(generateAdapter = true)
data class EmbeddedResources(
    @Json(name = "items") val items: List<ResourceItem>
)

@JsonClass(generateAdapter = true)
data class ResourceItem(
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String, // "dir" or "file"
    @Json(name = "path") val path: String,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "md5") val md5: String? = null,
    @Json(name = "sha256") val sha256: String? = null
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
    @Json(name = "title") val title: String,
    @Json(name = "currentProgressChar") val currentProgressChar: Int,
    @Json(name = "lastReadTime") val lastReadTime: Long
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
        @Query("limit") limit: Int = 100
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
}
