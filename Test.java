public class Test {
    public static void main(String[] args) {
        char c = '\u200B';
        System.out.println(Character.isWhitespace(c));
        System.out.println(Character.isSpaceChar(c));
    }
}
