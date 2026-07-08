import java.security.MessageDigest
import java.io.ByteArrayInputStream
import java.io.InputStream

fun computeSha1(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val hash = digest.digest(bytes)
    return hash.joinToString("") { "%02x".format(it) }
}

fun computeSha1Stream(inputStream: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val buffer = ByteArray(8192)
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
    }
    val hash = digest.digest()
    return hash.joinToString("") { "%02x".format(it) }
}

fun main() {
    val data = "Hello World".toByteArray()
    println(computeSha1(data))
    println(computeSha1Stream(ByteArrayInputStream(data)))
}
