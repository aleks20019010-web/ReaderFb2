import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

public class GenerateStars {
    public static void main(String[] args) throws Exception {
        int width = 1080;
        int height = 1920;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        GradientPaint bg = new GradientPaint(0, 0, new Color(13, 6, 30), 0, height, new Color(16, 37, 66));
        g.setPaint(bg);
        g.fillRect(0, 0, width, height);

        // Milky Way Nebula
        Point2D center = new Point2D.Float(width / 2f, height / 2f);
        float radius = height * 0.6f;
        float[] dist = {0.0f, 0.5f, 1.0f};
        Color[] colors = {new Color(74, 35, 100, 150), new Color(36, 91, 148, 80), new Color(0, 0, 0, 0)};
        RadialGradientPaint rgp = new RadialGradientPaint(center, radius, dist, colors);
        g.setPaint(rgp);
        g.fillRect(0, 0, width, height);
        
        // Milky way band (diagonal)
        Graphics2D g2 = (Graphics2D) g.create();
        g2.rotate(Math.toRadians(30), width/2, height/2);
        g2.scale(1.0, 3.0);
        RadialGradientPaint band = new RadialGradientPaint(center, radius/2, dist, new Color[]{new Color(100, 50, 150, 100), new Color(50, 50, 150, 50), new Color(0,0,0,0)});
        g2.setPaint(band);
        g2.fillRect(-width, -height, width*3, height*3);
        g2.dispose();

        // Stars
        Random rand = new Random(42);
        for (int i = 0; i < 2000; i++) {
            int x = rand.nextInt(width);
            int y = rand.nextInt(height);
            int r = rand.nextInt(3) + 1;
            int alpha = rand.nextInt(200) + 55;
            boolean isGold = rand.nextFloat() > 0.85f;
            g.setColor(new Color(isGold ? 255 : 255, isGold ? 215 : 255, isGold ? 0 : 255, alpha));
            g.fillOval(x, y, r, r);
        }

        g.dispose();
        ImageIO.write(img, "png", new File("app/src/main/res/drawable/bg_starry_night.png"));
    }
}
