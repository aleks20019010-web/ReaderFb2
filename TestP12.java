import java.security.KeyStore;
import java.io.FileInputStream;
public class TestP12 {
    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new FileInputStream("empty.jks"), "password".toCharArray());
    }
}
