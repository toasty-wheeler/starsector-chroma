package starchroma;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

/**
 * Off-game checks: renders scripted scenes to PNG (keyboard, mouse, mousepad, headset, keypad,
 * chromalink), checks a few colours, and in "live" mode plays a scripted sequence on the real
 * Razer devices through the same ChromaEngine the mod uses.
 */
public class EffectTest {

    static int failures;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        boolean live = args.length > 1 && "live".equals(args[1]);

        sheet(out);
        checks();
        if (failures > 0) {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
        System.out.println("checks passed");

        if (live) liveRun();
    }

    // ------------------------------------------------------------------ preview sheet

    static void sheet(File out) throws Exception {
        String[] names = {
            "campaign: 1,3 ready, 2 used, 4 just ready", "combat start", "shields rising (0.7 s)", "shields up, 50% flux",
            "100% flux, no shields", "phase cloak", "hull 60%", "hull 20%", "overloaded",
            "explosion 0.3 s", "explosion 0.9 s", "explosion 1.8 s", "after explosion"};
        int cellW = 760, cellH = 300;
        BufferedImage img = new BufferedImage(cellW * 2, cellH * ((names.length + 1) / 2), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 24, 28));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        for (int s = 0; s < names.length; s++) {
            Renderer r = new Renderer();
            long t0 = 100000L;
            int[][] f = scene(r, s, t0);
            int ox = (s % 2) * cellW, oy = (s / 2) * cellH;
            g.setColor(Color.WHITE);
            g.drawString(names[s], ox + 12, oy + 18);
            draw(g, f, ox + 12, oy + 28);
        }
        g.dispose();
        File file = new File(out, "effects.png");
        ImageIO.write(img, "png", file);
        System.out.println("wrote " + file);
    }

    /** puts the state for scene s in place, runs the renderer up to that moment, returns the frame */
    static int[][] scene(Renderer r, int s, long t0) {
        LightState.resetCombat();
        LightState.mode = LightState.COMBAT;
        LightState.hasShip = true;
        float run = 0.1f;
        switch (s) {
            case 0:
                LightState.mode = LightState.CAMPAIGN;
                LightState.slots = new int[] {1, 2, 2, 2, 0, 0, 0, 0, 0, 0};
                r.render(t0);
                LightState.slots = new int[] {1, 2, 1, 1, 0, 0, 0, 0, 0, 0};
                r.render(t0 + 100);
                LightState.slots = new int[] {1, 2, 1, 1, 0, 0, 0, 0, 0, 0};
                int[][] f0 = r.render(t0 + 140);
                return f0;
            case 1: break;
            case 2: LightState.shieldOn = true; run = 0.7f; break;
            case 3: LightState.shieldOn = true; LightState.flux = 0.5f; run = 2.5f; break;
            case 4: LightState.flux = 1f; break;
            case 5: LightState.phased = true; run = 2f; break;
            case 6: LightState.hull = 0.6f; break;
            case 7: LightState.hull = 0.2f; break;
            case 8: LightState.overloaded = true; LightState.flux = 1f; break;
            default:
                float[] at = {0.3f, 0.9f, 1.8f, 3.2f};
                LightState.dead = true;
                LightState.deathAt = t0;
                return r.render(t0 + (long) (at[s - 9] * 1000f));
        }
        int[][] f = null;
        for (long ms = 0; ms <= (long) (run * 1000f); ms += 40) f = r.render(t0 + ms);
        return f;
    }

    static void draw(Graphics2D g, int[][] f, int x, int y) {
        grid(g, f[Devices.KEYBOARD], 6, 22, x, y, 22, 2);
        grid(g, f[Devices.MOUSE], 9, 7, x + 520, y, 14, 2);
        grid(g, f[Devices.KEYPAD], 4, 5, x + 640, y, 16, 2);
        // mousepad drawn around a rectangle in LED order
        int px = x, py = y + 150, pw = 200, ph = 100;
        for (int i = 0; i < 15; i++) {
            int lx = px + (int) (Devices.X[Devices.MOUSEPAD][i] * pw);
            int ly = py + (int) ((1f - Devices.Y[Devices.MOUSEPAD][i]) * ph);
            g.setColor(new Color(f[Devices.MOUSEPAD][i]));
            g.fillRect(lx, ly, 14, 14);
        }
        grid(g, f[Devices.HEADSET], 1, 5, x + 260, y + 170, 22, 3);
        grid(g, f[Devices.CHROMALINK], 1, 5, x + 260, y + 210, 22, 3);
    }

    static void grid(Graphics2D g, int[] c, int rows, int cols, int x, int y, int size, int gap) {
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < cols; k++) {
                g.setColor(new Color(c[r * cols + k]));
                g.fillRect(x + k * (size + gap), y + r * (size + gap), size, size);
            }
        }
    }

    // ------------------------------------------------------------------ checks

    static void checks() {
        int kbTab = Devices.keyIndex(Devices.KEY_TAB);
        int key1 = Devices.keyIndex(Devices.KEY_DIGITS[0]);
        int key2 = Devices.keyIndex(Devices.KEY_DIGITS[1]);
        int key0 = Devices.keyIndex(Devices.KEY_DIGITS[9]);

        int[][] f = scene(new Renderer(), 0, 100000L);
        check("key 1 ready = light blue", f[0][key1], Config.ready);
        int u = f[0][key2];
        expect("key 2 used = red (" + Integer.toHexString(u) + ")", ((u >> 16) & 255) > 150 && 40 > ((u >> 8) & 255) && 40 > (u & 255));
        check("tab = light blue", f[0][kbTab], Config.ready);

        f = scene(new Renderer(), 1, 100000L);
        check("combat = light white", f[1][10], Config.combatWhite);

        f = scene(new Renderer(), 2, 100000L);
        int bottom = f[0][5 * 22 + 10], top = f[0][10];
        expect("shield 0.7 s: bottom row blue", near(bottom, Config.shield));
        expect("shield 0.7 s: top row still white", near(top, Config.combatWhite));

        f = scene(new Renderer(), 4, 100000L);
        check("full flux = red-orange", f[1][10], Config.fluxHot);

        f = scene(new Renderer(), 12, 100000L);
        expect("after explosion: tab lit", f[0][kbTab] != 0);
        int lit = 0;
        for (int d = 0; d < Devices.COUNT; d++) for (int i = 0; i < f[d].length; i++) if (f[d][i] != 0) lit++;
        expect("after explosion: only tab lit (" + lit + ")", lit == 1);

        String json = ChromaClient.encode(Devices.KEYBOARD, new int[132]);
        expect("keyboard json shape", json.startsWith("{\"effect\":\"CHROMA_CUSTOM\",\"param\":[[0,") && json.endsWith("0]]}"));
        expect("bgr swap", ChromaClient.bgr(0x112233) == 0x332211);
        expect("init uri parse", "http://localhost:54236/chromasdk".equals(
            ChromaClient.jsonString("{\"sessionid\":123,\"uri\":\"http:\\/\\/localhost:54236\\/chromasdk\"}", "uri")));
        expect("RZKEY_0 index", key0 == 22 + 11);
    }

    static boolean near(int a, int b) {
        return 12 >= Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
            && 12 >= Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
            && 12 >= Math.abs((a & 255) - (b & 255));
    }

    static void check(String what, int got, int want) {
        expect(what + " (got " + Integer.toHexString(got) + ", want " + Integer.toHexString(want) + ")", near(got, want));
    }

    static void expect(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    // ------------------------------------------------------------------ live

    static void liveRun() throws Exception {
        BasicConfigurator.configure();
        Logger log = Logger.getLogger(EffectTest.class);
        LightState.mode = LightState.CAMPAIGN;
        LightState.slots = new int[] {1, 1, 2, 1, 0, 0, 0, 0, 1, 2};
        ChromaEngine.start(log);
        say("campaign: keys 1,2,4,9 light blue, 3 and 0 red, Tab/E/C/F/R/I light blue");
        Thread.sleep(4000);
        LightState.slots = new int[] {1, 1, 1, 1, 0, 0, 0, 0, 1, 1};
        say("campaign: 3 and 0 ready again (white flash, then blue)");
        Thread.sleep(2500);

        LightState.resetCombat();
        LightState.hasShip = true;
        LightState.mode = LightState.COMBAT;
        say("combat: light white");
        Thread.sleep(2500);
        LightState.shieldOn = true;
        say("shields on: blue fills bottom to top");
        Thread.sleep(3000);
        say("flux rising");
        for (int i = 0; i <= 40; i++) { LightState.flux = i / 40f; Thread.sleep(75); }
        LightState.shieldOn = false;
        Thread.sleep(1500);
        say("hull damage");
        for (int i = 0; i <= 60; i++) { LightState.flux = 1f - i / 60f; LightState.hull = 1f - i / 70f; Thread.sleep(100); }
        Thread.sleep(1000);
        say("flagship destroyed");
        LightState.deathAt = System.currentTimeMillis();
        LightState.dead = true;
        Thread.sleep(5000);
        say("back to the campaign (other devices return to Synapse)");
        LightState.mode = LightState.CAMPAIGN;
        Thread.sleep(3000);
        LightState.mode = LightState.IDLE;
        Thread.sleep(500);
        ChromaEngine.shutdown();
        say("done, Chroma released");
    }

    static void say(String s) {
        System.out.println("[live] " + s);
    }
}
