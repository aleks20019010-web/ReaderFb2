import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;

public class ResizePng {
    public static void main(String[] args) throws Exception {
        File input = new File(args[0]);
        BufferedImage img = ImageIO.read(input);
        int w = Integer.parseInt(args[1]);
        int h = Integer.parseInt(args[2]);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        ImageIO.write(scaled, "png", new File(args[3]));
    }
}
