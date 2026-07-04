import java.security.KeyStore;
import java.io.FileInputStream;
public class TestJKS {
    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("empty.jks"), "password".toCharArray());
    }
}
