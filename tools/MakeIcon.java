import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
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
 * glowing rainbow (Chroma-style) outline, and the outline of a ship sprite turned 45 degrees,
 * glowing in a rainbow sweep from nose to tail, with engine glow.
 *
 * Usage: MakeIcon <ship sprite png> <output png>   (e.g. MakeIcon tools/ship.png icon.png)
 */
public class MakeIcon {

    static final int S = 256;
    /** supersampling for the traced outline */
    static final int K = 3;

    public static void main(String[] args) throws Exception {
        BufferedImage sprite = ImageIO.read(new File(args[0]));
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

        // ---- ship: silhouette of the sprite, nose turned to the top right
        int[] box = opaqueBounds(sprite);
        float bw = box[2] - box[0] + 1, bh = box[3] - box[1] + 1;
        float cx = box[0] + bw / 2f, cy = box[1] + bh / 2f;
        float scale = 196f / ((bw + bh) * 0.7071f);
        AffineTransform at = new AffineTransform();
        at.translate(S / 2f, S / 2f + 2f);
        at.rotate(Math.PI / 4);
        at.scale(scale, scale);
        at.translate(-cx, -cy);

        int W = S * K;
        BufferedImage big = new BufferedImage(W, W, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = big.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        AffineTransform bt = AffineTransform.getScaleInstance(K, K);
        bt.concatenate(at);
        bg.drawImage(sprite, bt, null);
        bg.dispose();

        // smooth the pixel-art stair steps, then threshold to a clean silhouette
        float[] a = new float[W * W];
        for (int i = 0; i < a.length; i++) a[i] = ((big.getRGB(i % W, i / W) >>> 24) & 255) / 255f;
        blur(a, W, W, K);
        boolean[] in = new boolean[a.length];
        for (int i = 0; i < a.length; i++) in[i] = a[i] > 0.5f;

        // outline band: pixels within R of the silhouette edge (both sides)
        float R = 1.25f * K;
        int ri = (int) Math.ceil(R);
        float[] band = new float[S * S];
        float[] fill = new float[S * S];
        float sub = 1f / (K * K);
        for (int y = 0; y < W; y++) {
            for (int x = 0; x < W; x++) {
                boolean me = in[y * W + x];
                boolean edge = false;
                for (int dy = -ri; ri >= dy && !edge; dy++) {
                    int yy = y + dy;
                    if (0 > yy || yy >= W) continue;
                    for (int dx = -ri; ri >= dx; dx++) {
                        if (dx * dx + dy * dy > R * R) continue;
                        int xx = x + dx;
                        if (0 > xx || xx >= W) continue;
                        if (in[yy * W + xx] != me) {
                            edge = true;
                            break;
                        }
                    }
                }
                int o = (y / K) * S + (x / K);
                if (edge) band[o] += sub;
                if (me) fill[o] += sub;
            }
        }

        // ship axis in the icon, tail -> nose, for the rainbow sweep
        Point2D nose = at.transform(new Point2D.Float(cx, box[1]), null);
        Point2D tail = at.transform(new Point2D.Float(cx, box[3]), null);
        float tx = (float) tail.getX(), ty = (float) tail.getY();
        float ax = (float) nose.getX() - tx, ay = (float) nose.getY() - ty;
        float len2 = ax * ax + ay * ay;

        // dim the stars behind the hull so the outline reads as a ship
        for (int i = 0; i < fill.length; i++) {
            if (fill[i] > 0f) blend(img, i % S, i / S, 0x05060B, fill[i] * 0.72f);
        }

        // engine glow behind the tail
        float ex = tx - ax * 0.05f, ey = ty - ay * 0.05f;
        glow(g, ex, ey, 24, 0x7FE8FF);

        // glow passes of the outline, then the crisp line with a hot white core
        float[] soft = (float[]) band.clone();
        blur(soft, S, S, 2);
        blur(soft, S, S, 2);
        blur(soft, S, S, 2);
        float[] wide = (float[]) soft.clone();
        blur(wide, S, S, 6);
        for (int i = 0; i < band.length; i++) {
            int x = i % S, y = i / S;
            float t = ((x - tx) * ax + (y - ty) * ay) / len2;
            float h = 0.02f + 0.80f * Math.max(0f, Math.min(1f, 1f - t));
            int col = hue(h).getRGB() & 0xFFFFFF;
            float gw = Math.min(1f, wide[i] * 2.5f) * 0.25f + Math.min(1f, soft[i] * 2f) * 0.35f;
            if (gw > 0.003f) blend(img, x, y, col, gw);
            if (band[i] > 0f) {
                blend(img, x, y, col, Math.min(1f, band[i] * 1.15f));
                blend(img, x, y, 0xFFFFFF, band[i] * band[i] * 0.35f);
            }
        }
        glow(g, ex, ey, 8, 0xFFFFFF);

        g.dispose();
        File out = new File(args[1]);
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    /** minX, minY, maxX, maxY of the pixels that are mostly opaque */
    static int[] opaqueBounds(BufferedImage im) {
        int[] b = {im.getWidth(), im.getHeight(), -1, -1};
        for (int y = 0; y < im.getHeight(); y++) {
            for (int x = 0; x < im.getWidth(); x++) {
                if (((im.getRGB(x, y) >>> 24) & 255) > 100) {
                    b[0] = Math.min(b[0], x);
                    b[1] = Math.min(b[1], y);
                    b[2] = Math.max(b[2], x);
                    b[3] = Math.max(b[3], y);
                }
            }
        }
        return b;
    }

    /** separable box blur in place */
    static void blur(float[] v, int w, int h, int r) {
        float[] tmp = new float[v.length];
        float n = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0f;
                for (int k = -r; r >= k; k++) {
                    int xx = Math.max(0, Math.min(w - 1, x + k));
                    s += v[y * w + xx];
                }
                tmp[y * w + x] = s / n;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0f;
                for (int k = -r; r >= k; k++) {
                    int yy = Math.max(0, Math.min(h - 1, y + k));
                    s += tmp[yy * w + x];
                }
                v[y * w + x] = s / n;
            }
        }
    }

    /** source-over of an opaque colour at the given alpha */
    static void blend(BufferedImage img, int x, int y, int rgb, float alpha) {
        if (0f >= alpha) return;
        if (alpha > 1f) alpha = 1f;
        int d = img.getRGB(x, y);
        int da = (d >>> 24) & 255;
        int r = Math.round(((d >> 16) & 255) * (1 - alpha) + ((rgb >> 16) & 255) * alpha);
        int gg = Math.round(((d >> 8) & 255) * (1 - alpha) + ((rgb >> 8) & 255) * alpha);
        int b = Math.round((d & 255) * (1 - alpha) + (rgb & 255) * alpha);
        int na = Math.round(da + (255 - da) * alpha);
        img.setRGB(x, y, (na << 24) | (r << 16) | (gg << 8) | b);
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

    static void glow(Graphics2D g, float cx, float cy, float radius, int rgb) {
        Color c = new Color(rgb);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius, new float[] {0f, 0.45f, 1f},
            new Color[] {new Color(c.getRed(), c.getGreen(), c.getBlue(), 200), new Color(c.getRed(), c.getGreen(), c.getBlue(), 70),
                new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
    }
}
