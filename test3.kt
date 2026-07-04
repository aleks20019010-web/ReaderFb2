import java.security.KeyStore
import java.io.FileInputStream
fun main() {
    val ks = KeyStore.getInstance("JKS")
    ks.load(FileInputStream("empty.jks"), "password".toCharArray())
}
