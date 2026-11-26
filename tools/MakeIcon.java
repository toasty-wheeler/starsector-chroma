import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Draws icon.png (256 x 256, shown by mod managers such as TriOS): a dark space tile holding a
 * grid of glowing keycaps whose colour sweeps from shield blue through white to flux red-orange,
 * with a four-point star flare over the middle.
 *
 * Usage: MakeIcon <output png>
 */
public class MakeIcon {

    static final int BLUE = 0x55C8FF;
    static final int WHITE = 0xE6EBF5;
    static final int HOT = 0xFF4A10;

    public static void main(String[] args) throws Exception {
        int S = 256;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // bezel + dark space face
        Shape outer = new RoundRectangle2D.Float(8, 8, 240, 240, 52, 52);
        g.setPaint(new GradientPaint(0, 8, new Color(0x4A505E), 0, 248, new Color(0x16181F)));
        g.fill(outer);
        Shape face = new RoundRectangle2D.Float(16, 16, 224, 224, 44, 44);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(128, 110), 190f, new float[] {0f, 1f},
            new Color[] {new Color(0x1A2233), new Color(0x07080D)}));
        g.fill(face);
        g.setClip(face);

        // stars
        Random rnd = new Random(7);
        for (int i = 0; i < 70; i++) {
            float x = 16 + rnd.nextFloat() * 224, y = 16 + rnd.nextFloat() * 224;
            float r = 0.5f + rnd.nextFloat() * 1.1f;
            g.setColor(new Color(1f, 1f, 1f, 0.25f + rnd.nextFloat() * 0.5f));
            g.fill(new java.awt.geom.Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
        }

        // keycaps: 5 x 4, colour sweeps diagonally bottom-left (blue) -> white -> top-right (hot)
        int cols = 5, rows = 4;
        float key = 34, gap = 8;
        float gw = cols * key + (cols - 1) * gap, gh = rows * key + (rows - 1) * gap;
        float ox = (S - gw) / 2f, oy = (S - gh) / 2f;
        // glow pass first, then the caps on top
        for (int pass = 0; pass < 2; pass++) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    float x = ox + c * (key + gap), y = oy + r * (key + gap);
                    float t = (c / (float) (cols - 1) + (rows - 1 - r) / (float) (rows - 1)) / 2f; // 0 bottom-left .. 1 top-right
                    int col = 0.5f > t ? lerp(BLUE, WHITE, t * 2f) : lerp(WHITE, HOT, (t - 0.5f) * 2f);
                    if (pass == 0) glow(g, x + key / 2, y + key / 2, key * 1.05f, col);
                    else cap(g, x, y, key, col);
                }
            }
        }

        // four-point star flare in the middle of the grid
        float cx = S / 2f, cy = oy + gh / 2f;
        glow(g, cx, cy, 46, 0xFFFFFF);
        GeneralPath star = new GeneralPath();
        float L = 40, w = 6;
        star.moveTo(cx, cy - L);
        star.quadTo(cx + w * 0.3f, cy - w * 0.3f, cx + L, cy);
        star.quadTo(cx + w * 0.3f, cy + w * 0.3f, cx, cy + L);
        star.quadTo(cx - w * 0.3f, cy + w * 0.3f, cx - L, cy);
        star.quadTo(cx - w * 0.3f, cy - w * 0.3f, cx, cy - L);
        star.closePath();
        g.setColor(Color.WHITE);
        g.fill(star);

        g.setClip(null);

        // bezel rim
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 255, 255, 40));
        g.draw(new RoundRectangle2D.Float(9, 9, 238, 238, 50, 50));

        g.dispose();
        File out = new File(args[0]);
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    static void glow(Graphics2D g, float cx, float cy, float radius, int rgb) {
        Color c = new Color(rgb);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius, new float[] {0f, 0.45f, 1f},
            new Color[] {new Color(c.getRed(), c.getGreen(), c.getBlue(), 150), new Color(c.getRed(), c.getGreen(), c.getBlue(), 55),
                new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new java.awt.geom.Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    static void cap(Graphics2D g, float x, float y, float s, int rgb) {
        Color c = new Color(rgb);
        // dark key body with the lit colour bleeding around the edge
        g.setColor(new Color(lerp(rgb, 0x000000, 0.35f)));
        g.fill(new RoundRectangle2D.Float(x, y, s, s, 9, 9));
        // face: lit top fading down
        g.setPaint(new GradientPaint(0, y + 3, new Color(lerp(rgb, 0xFFFFFF, 0.35f)), 0, y + s - 6, c));
        g.fill(new RoundRectangle2D.Float(x + 3, y + 2, s - 6, s - 7, 7, 7));
        g.setComposite(AlphaComposite.SrcOver);
    }

    static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gg = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (gg << 8) | bl;
    }
}
