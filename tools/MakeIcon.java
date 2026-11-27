import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Draws icon.png (256 x 256, shown by mod managers such as TriOS): a dark space tile with a
 * glowing rainbow (Chroma-style) outline, and a top-down warship in the middle painted in a
 * rainbow sweep, with engine glow.
 *
 * Usage: MakeIcon <output png>
 */
public class MakeIcon {

    public static void main(String[] args) throws Exception {
        int S = 256;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // dark tile
        Shape tile = new RoundRectangle2D.Float(6, 6, 244, 244, 54, 54);
        g.setColor(new Color(0x0B0C12));
        g.fill(tile);
        Shape face = new RoundRectangle2D.Float(18, 18, 220, 220, 42, 42);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(128, 120), 170f, new float[] {0f, 1f},
            new Color[] {new Color(0x1A1F33), new Color(0x06070C)}));
        g.fill(face);

        // stars
        g.setClip(face);
        Random rnd = new Random(7);
        for (int i = 0; i < 80; i++) {
            float x = 18 + rnd.nextFloat() * 220, y = 18 + rnd.nextFloat() * 220;
            float r = 0.5f + rnd.nextFloat() * 1.1f;
            g.setColor(new Color(1f, 1f, 1f, 0.2f + rnd.nextFloat() * 0.5f));
            g.fill(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
        }
        g.setClip(null);

        // RGB outline: hue runs around the tile; soft glow passes, then the crisp line
        Shape ring = new RoundRectangle2D.Float(12, 12, 232, 232, 48, 48);
        rainbowStroke(g, ring, 16f, 0.10f);
        rainbowStroke(g, ring, 11f, 0.18f);
        rainbowStroke(g, ring, 5f, 1f);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(255, 255, 255, 90));
        g.draw(ring);

        // ship
        GeneralPath ship = shipShape();
        Color[] rainbow = new Color[] {
            hue(0.00f), hue(0.10f), hue(0.18f), hue(0.33f), hue(0.50f), hue(0.62f), hue(0.78f), hue(0.90f)};
        float[] stops = new float[] {0f, 0.14f, 0.28f, 0.42f, 0.56f, 0.70f, 0.85f, 1f};

        // engine plumes behind the ship
        glow(g, 111, 212, 22, 0x7FE8FF);
        glow(g, 145, 212, 22, 0x7FE8FF);
        // coloured glow around the hull
        g.setPaint(new LinearGradientPaint(70, 44, 186, 206, stops, translucent(rainbow, 70)));
        g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(ship);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new LinearGradientPaint(70, 44, 186, 206, stops, translucent(rainbow, 110)));
        g.draw(ship);

        // hull in a rainbow sweep
        g.setPaint(new LinearGradientPaint(70, 44, 186, 206, stops, rainbow));
        g.fill(ship);
        // shading: lighter spine, darker wing tips
        g.setClip(ship);
        g.setPaint(new LinearGradientPaint(44, 0, 212, 0, new float[] {0f, 0.5f, 1f},
            new Color[] {new Color(0, 0, 0, 110), new Color(255, 255, 255, 70), new Color(0, 0, 0, 110)}));
        g.fill(ship);
        g.setClip(null);

        // panel lines
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(10, 10, 20, 150));
        g.draw(new Line2D.Float(128, 70, 128, 184));
        g.draw(new Line2D.Float(146, 118, 196, 160));
        g.draw(new Line2D.Float(110, 118, 60, 160));
        g.draw(new Line2D.Float(140, 150, 152, 176));
        g.draw(new Line2D.Float(116, 150, 104, 176));
        // dark hull outline
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(8, 8, 16, 200));
        g.draw(ship);

        // cockpit
        g.setColor(new Color(0x0A0D1A));
        g.fill(new Ellipse2D.Float(121, 86, 14, 24));
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(new Ellipse2D.Float(125, 89, 4, 9));

        // engine cores
        glow(g, 111, 206, 9, 0xFFFFFF);
        glow(g, 145, 206, 9, 0xFFFFFF);

        g.dispose();
        File out = new File(args[0]);
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    /** top-down warship pointing up, mirrored around x = 128 */
    static GeneralPath shipShape() {
        float[][] half = {
            {128, 42}, {137, 64}, {142, 98}, {150, 114}, {184, 134}, {210, 160}, {208, 174},
            {172, 168}, {158, 176}, {156, 198}, {150, 204}, {138, 204}, {134, 190}, {128, 190}};
        GeneralPath p = new GeneralPath();
        p.moveTo(half[0][0], half[0][1]);
        for (int i = 1; i < half.length; i++) p.lineTo(half[i][0], half[i][1]);
        for (int i = half.length - 2; i >= 1; i--) p.lineTo(256 - half[i][0], half[i][1]);
        p.closePath();
        return p;
    }

    /** strokes a shape segment by segment, the hue following the angle around the icon centre */
    static void rainbowStroke(Graphics2D g, Shape s, float width, float alpha) {
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        PathIterator it = s.getPathIterator(null, 0.4);
        float[] c = new float[6];
        float sx = 0, sy = 0, lx = 0, ly = 0;
        while (!it.isDone()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO) {
                sx = lx = c[0];
                sy = ly = c[1];
            } else {
                float x = type == PathIterator.SEG_CLOSE ? sx : c[0];
                float y = type == PathIterator.SEG_CLOSE ? sy : c[1];
                double a = Math.atan2((ly + y) / 2 - 128, (lx + x) / 2 - 128);
                float h = (float) ((a + Math.PI) / (2 * Math.PI));
                Color col = hue(h);
                g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), Math.round(alpha * 255)));
                g.draw(new Line2D.Float(lx, ly, x, y));
                lx = x;
                ly = y;
            }
            it.next();
        }
    }

    static Color hue(float h) {
        return Color.getHSBColor(h, 0.85f, 1f);
    }

    static Color[] translucent(Color[] cs, int a) {
        Color[] out = new Color[cs.length];
        for (int i = 0; i < cs.length; i++) out[i] = new Color(cs[i].getRed(), cs[i].getGreen(), cs[i].getBlue(), a);
        return out;
    }

    static void glow(Graphics2D g, float cx, float cy, float radius, int rgb) {
        Color c = new Color(rgb);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius, new float[] {0f, 0.45f, 1f},
            new Color[] {new Color(c.getRed(), c.getGreen(), c.getBlue(), 200), new Color(c.getRed(), c.getGreen(), c.getBlue(), 70),
                new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
    }
}
