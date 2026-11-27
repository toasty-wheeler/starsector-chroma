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
        campaignSheet(out);
        systemSheet(out);
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

    static void campaignSheet(File out) throws Exception {
        String[] names = {
            "normal (dim end of the breath)", "normal (bright end)", "running silent", "running silent +1.5 s",
            "sustained burn", "sustained burn +0.4 s", "emergency burn", "emergency burn +0.2 s",
            "neutrino detector", "sensor burst charging", "sensor burst ring 0.35 s", "sensor burst ring 0.8 s",
            "interdiction charging 40%", "interdiction charging 95%", "interdiction fires 0.06 s", "interdiction pulse 0.5 s",
            "transverse jump 40%", "transverse jump 100%", "jumped: receding 0.15 s", "jumped: receding 0.3 s"};
        int cellW = 560, cellH = 190;
        BufferedImage img = new BufferedImage(cellW * 2, cellH * ((names.length + 1) / 2), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 24, 28));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int s = 0; s < names.length; s++) {
            int[][] f = campaignScene(new Renderer(), s);
            int ox = (s % 2) * cellW, oy = (s / 2) * cellH;
            g.setColor(Color.WHITE);
            g.drawString(names[s], ox + 12, oy + 18);
            grid(g, f[Devices.KEYBOARD], 6, 22, ox + 12, oy + 28, 22, 2);
        }
        g.dispose();
        File file = new File(out, "campaign.png");
        ImageIO.write(img, "png", file);
        System.out.println("wrote " + file);
    }

    static int[][] campaignScene(Renderer r, int s) {
        long t0 = 3600000L * 10; // t = 0 in the renderer's clock
        LightState.mode = LightState.CAMPAIGN;
        LightState.slots = new int[] {1, 1, 2, 1, 1, 0, 0, 0, 0, 0};
        LightState.move = LightState.MOVE_NORMAL;
        LightState.neutrino = false;
        LightState.sensorCharge = 0f;
        LightState.pulseCharge = 0f;
        LightState.jumpCharge = 0f;
        LightState.sensorAt = 0L;
        LightState.pulseAt = 0L;
        LightState.jumpDoneAt = 0L;
        switch (s) {
            case 0: return r.render(t0);
            case 1: return r.render(t0 + 2244);
            case 2: LightState.move = LightState.MOVE_SILENT; return r.render(t0 + 5000);
            case 3: LightState.move = LightState.MOVE_SILENT; return r.render(t0 + 6500);
            case 4: LightState.move = LightState.MOVE_SUSTAINED; return r.render(t0 + 5000);
            case 5: LightState.move = LightState.MOVE_SUSTAINED; return r.render(t0 + 5400);
            case 6: LightState.move = LightState.MOVE_EMERGENCY; return r.render(t0 + 5000);
            case 7: LightState.move = LightState.MOVE_EMERGENCY; return r.render(t0 + 5200);
            case 8: LightState.neutrino = true; return r.render(t0 + 1500);
            case 9: LightState.sensorCharge = 0.8f; return r.render(t0 + 1000);
            case 10: LightState.sensorAt = t0 + 1000; return r.render(t0 + 1350);
            case 11: LightState.sensorAt = t0 + 1000; return r.render(t0 + 1800);
            case 12: LightState.pulseCharge = 0.4f; return r.render(t0 + 1000);
            case 13: LightState.pulseCharge = 0.95f; return r.render(t0 + 1000);
            case 14: LightState.pulseAt = t0 + 1000; return r.render(t0 + 1060);
            case 15: LightState.pulseAt = t0 + 1000; return r.render(t0 + 1500);
            default:
                float charge = s == 16 ? 0.4f : 1f;
                int[][] f = null;
                long ms = 0;
                for (; 1500 >= ms; ms += 40) {
                    LightState.jumpCharge = Math.min(charge, ms / 1200f + 0.01f);
                    f = r.render(t0 + ms);
                }
                if (s >= 18) {
                    LightState.jumpCharge = 0f;
                    LightState.jumpDoneAt = t0 + ms;
                    long end = ms + (s == 18 ? 150 : 300);
                    for (; end >= ms; ms += 40) f = r.render(t0 + ms);
                }
                return f;
        }
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

    // ------------------------------------------------------------------ ship systems

    static final String[] SYS_NAMES = {
        "burn drive ready (F light blue)", "burn drive on 0.6 s", "burn drive recharging (F red)",
        "phase skimmer: collapse 0.2 s", "phase teleporter: in transit", "teleport: reappear 0.25 s after",
        "phase cloak (system) 1.5 s", "accelerated ammo feeder 1 s", "other system (damper) 1 s",
        "instant skimmer: burst 0.3 s", "hull 50%: red/orange alarm keys", "mouse sides: ammo 60% / skimmer 1 of 3 + reloading",
        "mouse sides: ammo 15% (low) / cooldown half way", "overload while ship gone (must be plain)",
        "crew readiness 25%: yellow outline", "crew readiness 10%: red outline"};
    // type, on from ms, on until ms, render at ms
    static final int[][] SYS_SCENES = {
        {LightState.SYS_CHARGE, -1, -1, 400},
        {LightState.SYS_CHARGE, 0, 5000, 600},
        {LightState.SYS_CHARGE, 0, 1000, 1600},
        {LightState.SYS_TELEPORT, 0, 2000, 200},
        {LightState.SYS_TELEPORT, 0, 2000, 900},
        {LightState.SYS_TELEPORT, 0, 600, 850},
        {LightState.SYS_PHASE, 0, 5000, 1500},
        {LightState.SYS_FEEDER, 0, 5000, 1000},
        {LightState.SYS_OTHER, 0, 5000, 1000},
        {LightState.SYS_TELEPORT, 0, 80, 650},
        {LightState.SYS_NONE, -1, -1, 400},
        {LightState.SYS_TELEPORT, -1, -1, 400},
        {LightState.SYS_CHARGE, -1, -1, 400},
        {LightState.SYS_NONE, -1, -1, 400},
        {LightState.SYS_NONE, -1, -1, 1250},
        {LightState.SYS_NONE, -1, -1, 715}};

    static int[][] sysScene(Renderer r, int s, long t0) {
        LightState.resetCombat();
        LightState.mode = LightState.COMBAT;
        LightState.hasShip = true;
        int[] sc = SYS_SCENES[s];
        LightState.systemType = sc[0];
        LightState.systemReady = true;
        int[][] f = null;
        boolean was = false;
        for (long ms = 0; sc[3] >= ms; ms += 20) {
            boolean on = ms >= sc[1] && sc[1] >= 0 && sc[2] > ms;
            if (on && !was) LightState.systemAt = t0 + ms;
            if (!on && was) LightState.systemEndAt = t0 + ms;
            was = on;
            LightState.systemOn = on;
            LightState.systemLevel = on ? Math.min(1f, (ms - sc[1]) / 300f) : 0f;
            LightState.systemReady = !on && LightState.systemEndAt == 0L;
            switch (s) {
                case 10: LightState.hull = 0.5f; break;
                case 11: LightState.ammo = 0.6f; LightState.systemMaxCharges = 3; LightState.systemCharges = 1;
                    LightState.systemRecharge = 0.5f; break;
                case 12: LightState.ammo = 0.15f; LightState.systemReady = false; LightState.systemRecharge = 0.5f; break;
                case 13: LightState.overloaded = true; LightState.hasShip = false; break;
                case 14: LightState.cr = 0.25f; break;
                case 15: LightState.cr = 0.10f; break;
                default: break;
            }
            f = r.render(t0 + ms);
        }
        return f;
    }

    static void systemSheet(File out) throws Exception {
        int cellW = 760, cellH = 300;
        BufferedImage img = new BufferedImage(cellW * 2, cellH * ((SYS_NAMES.length + 1) / 2), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 24, 28));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int s = 0; s < SYS_NAMES.length; s++) {
            int[][] f = sysScene(new Renderer(), s, 100000L);
            int ox = (s % 2) * cellW, oy = (s / 2) * cellH;
            g.setColor(Color.WHITE);
            g.drawString(SYS_NAMES[s], ox + 12, oy + 18);
            draw(g, f, ox + 12, oy + 28);
        }
        g.dispose();
        File file = new File(out, "systems.png");
        ImageIO.write(img, "png", file);
        System.out.println("wrote " + file);
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
        check("D = light blue", f[0][Devices.keyIndex(Devices.KEY_D)], Config.ready);
        check("F2 (codex) = light blue", f[0][Devices.keyIndex(Devices.KEY_F2)], Config.ready);
        check("F4 (quicksave) = light blue", f[0][Devices.keyIndex(Devices.KEY_F4)], Config.ready);
        check("F5 (quickload) = light blue", f[0][Devices.keyIndex(Devices.KEY_F5)], Config.ready);

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

        // ship systems
        int kbF = Devices.keyIndex(Devices.KEY_F);
        f = sysScene(new Renderer(), 0, 100000L);
        check("system ready: F light blue", f[0][kbF], Config.ready);
        f = sysScene(new Renderer(), 2, 100000L);
        int fr = f[0][kbF];
        expect("system recharging: F red (" + Integer.toHexString(fr) + ")", ((fr >> 16) & 255) > 150 && 40 > ((fr >> 8) & 255));
        f = sysScene(new Renderer(), 4, 100000L);
        int bright = 0;
        for (int i = 0; i < f[0].length; i++) if (((f[0][i] >> 8) & 255) > 0x60) bright++;
        expect("teleport in transit: keyboard mostly dark (" + bright + " bright)", 30 > bright);
        f = sysScene(new Renderer(), 1, 100000L);
        expect("burn drive: tinted warm", ((f[1][10] >> 16) & 255) > (f[1][10] & 255) + 40);
        f = sysScene(new Renderer(), 13, 100000L);
        expect("overload without a ship: no crackle", near(f[1][10], Config.combatWhite));
        f = sysScene(new Renderer(), 10, 100000L);
        int alarm = 0;
        for (int i = 0; i < f[0].length; i++) {
            int c = f[0][i];
            if (((c >> 16) & 255) > 60 && 20 > (c & 255)) alarm++;
        }
        expect("hull 50%: some red/orange alarm keys (" + alarm + ")", alarm >= 3 && 60 > alarm);
        f = sysScene(new Renderer(), 11, 100000L);
        int[] m = f[Devices.MOUSE];
        expect("mouse left bottom = ammo brass", near(m[7 * 7], Renderer.AMMO_BRASS));
        expect("mouse left top = empty (60% ammo)", 0x30 > ((m[1 * 7] >> 16) & 255));
        check("mouse right bottom = charge ready", m[7 * 7 + 6], Config.ready);
        expect("mouse right top = no charge", 0x20 > ((m[1 * 7 + 6] >> 16) & 255));
        f = sysScene(new Renderer(), 14, 100000L);
        check("CR 25%: keyboard corner yellow", f[0][5 * 22 + 1], Renderer.CR_YELLOW);
        check("CR 25%: mousepad yellow", f[Devices.MOUSEPAD][3], Renderer.CR_YELLOW);
        check("CR 25%: inner key untouched", f[0][3 * 22 + 8], Config.combatWhite);
        f = sysScene(new Renderer(), 15, 100000L);
        check("CR 10%: number row red", f[0][1 * 22 + 6], Renderer.CR_RED);
        f = sysScene(new Renderer(), 1, 100000L);
        expect("full CR: no outline", near(f[0][5 * 22 + 1], Config.combatWhite) || f[0][5 * 22 + 1] != Renderer.CR_YELLOW);
        String[][] ids = {
            {"burndrive", "1"}, {"microburn_omega", "1"}, {"maneuveringjets", "1"}, {"plasmajets", "1"}, {"convulsive_lunge", "1"},
            {"phaseteleporter", "2"}, {"displacer", "2"}, {"skimmer_drone", "2"}, {"phasecloak", "3"},
            {"ammofeed", "4"}, {"fastmissileracks", "4"}, {"highenergyfocus", "4"}, {"targetingfeed", "4"},
            {"damper", "5"}, {"fortressshield", "5"}, {"temporalshell", "5"}};
        for (int k = 0; k < ids.length; k++) {
            int got = LightState.classifySystem(ids[k][0]);
            expect("system " + ids[k][0] + " -> " + ids[k][1] + " (got " + got + ")", got == Integer.parseInt(ids[k][1]));
        }
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
        say("campaign: keys 1,2,4,9 light blue, 3 and 0 red, Tab/E/C/F/R/I light blue; normal travel breathing");
        Thread.sleep(5000);
        LightState.move = LightState.MOVE_SILENT;
        say("running silent");
        Thread.sleep(4000);
        LightState.move = LightState.MOVE_SUSTAINED;
        say("sustained burn");
        Thread.sleep(4000);
        LightState.move = LightState.MOVE_EMERGENCY;
        say("emergency burn");
        Thread.sleep(4000);
        LightState.move = LightState.MOVE_NORMAL;
        LightState.neutrino = true;
        say("neutrino detector");
        Thread.sleep(4000);
        LightState.neutrino = false;
        say("active sensor burst");
        for (int i = 1; i <= 20; i++) { LightState.sensorCharge = i / 20f; Thread.sleep(50); }
        LightState.sensorCharge = 0f;
        LightState.sensorAt = System.currentTimeMillis();
        Thread.sleep(2000);
        say("interdiction pulse");
        for (int i = 1; i <= 40; i++) { LightState.pulseCharge = i / 40f; Thread.sleep(50); }
        LightState.pulseCharge = 0f;
        LightState.pulseAt = System.currentTimeMillis();
        Thread.sleep(2000);
        say("transverse jump");
        for (int i = 1; i <= 50; i++) { LightState.jumpCharge = i / 50f; Thread.sleep(50); }
        Thread.sleep(800);
        LightState.jumpCharge = 0f;
        LightState.jumpDoneAt = System.currentTimeMillis();
        Thread.sleep(2000);
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
