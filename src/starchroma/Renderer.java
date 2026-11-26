package starchroma;

/**
 * Turns LightState into colours for every LED. Pure Java, no game classes, so it can be tested
 * outside the game. Colours are 0xRRGGBB. Holds a little animation state (shield fill, hit flash).
 */
public class Renderer {

    public static final float EXPLOSION_SECONDS = 2.6f;

    private final int[][] frame = new int[Devices.COUNT][];

    // animation state
    private float shieldFill;        // 0..1 height reached by the shield colour
    private float phaseFill;
    private float lastHull = 1f;
    private float hitFlash;
    private boolean hadShip;
    private long lastMs;
    private int[] lastSlots = new int[10];
    private final float[] readyFlash = new float[10];

    public Renderer() {
        for (int d = 0; d < Devices.COUNT; d++) frame[d] = new int[Devices.size(d)];
    }

    public void resetCombat() {
        shieldFill = 0f;
        phaseFill = 0f;
        lastHull = 1f;
        hadShip = false;
        hitFlash = 0f;
    }

    /** Renders the current state. Returned arrays are reused between calls. */
    public int[][] render(long nowMs) {
        float dt = lastMs == 0L ? 0f : (nowMs - lastMs) / 1000f;
        if (dt > 0.25f) dt = 0.25f;
        if (0f > dt) dt = 0f;
        lastMs = nowMs;
        float t = (nowMs % 3600000L) / 1000f;

        int mode = LightState.mode;
        if (mode == LightState.COMBAT) renderCombat(t, dt, nowMs);
        else renderCampaign(t, dt, nowMs);

        if (1f > Config.brightness) {
            for (int d = 0; d < Devices.COUNT; d++) {
                int[] a = frame[d];
                for (int i = 0; i < a.length; i++) a[i] = scale(a[i], Config.brightness);
            }
        }
        return frame;
    }

    // ------------------------------------------------------------------ campaign

    // background colours
    static final int SILENT_DARK = 0x0E0318, SILENT_LIGHT = 0x2C0E50;
    static final int NEUTRINO = 0x2E7BFF;
    static final int PULSE = 0x9FDCFF;
    static final int TENDRIL_DARK = 0x22073F, TENDRIL = 0x8E34F0, TENDRIL_TIP = 0xE0B0FF;
    static final int[] STARS_SUSTAINED = {0xFFD040, 0xFF8A20, 0xFF3A10};
    static final int[] STARS_EMERGENCY = {0xFF2A00, 0xB00000, 0xFF5000};

    // transverse jump animation
    private float jumpLevel;
    private float lastJumpCharge;
    private long jumpHoldUntil;
    private long jumpFastUntil;
    private long jumpDoneSeen;

    private void renderCampaign(float t, float dt, long now) {
        boolean status = "status".equals(Config.baseMode);
        if (status) updateJump(dt, now);

        for (int d = 0; d < Devices.COUNT; d++) {
            int[] a = frame[d];
            float[] xs = Devices.X[d];
            float[] ys = Devices.Y[d];
            float aspect = Devices.ASPECT[d];
            for (int i = 0; i < a.length; i++) {
                if (status) a[i] = statusPixel(xs[i], ys[i], aspect, t, now);
                else a[i] = base(xs[i], t);
            }
        }
        int[] kb = frame[Devices.KEYBOARD];

        int[] slots = LightState.slots;
        for (int s = 0; s < 10; s++) {
            int st = slots[s];
            if (st == LightState.SLOT_READY && lastSlots[s] == LightState.SLOT_USED) readyFlash[s] = 1f;
            readyFlash[s] = Math.max(0f, readyFlash[s] - dt * 2.5f);
            int idx = Devices.keyIndex(Devices.KEY_DIGITS[s]);
            if (st == LightState.SLOT_READY) {
                kb[idx] = lerp(Config.ready, 0xFFFFFF, readyFlash[s]);
            } else if (st == LightState.SLOT_USED) {
                kb[idx] = scale(Config.used, 0.85f + 0.15f * (float) Math.sin(t * 5f));
            }
        }
        lastSlots = slots;

        for (int k = 0; k < Devices.KEY_SCREENS.length; k++) {
            kb[Devices.keyIndex(Devices.KEY_SCREENS[k])] = Config.ready;
        }
    }

    /** older fixed backgrounds ("spectrum", "wave", "static") */
    private int base(float x, float t) {
        String m = Config.baseMode;
        float v = Config.baseBrightness;
        if ("static".equals(m)) return scale(Config.baseColor, v);
        if ("wave".equals(m)) return hsv(frac(t * 0.12f - x * 0.8f), 1f, v);
        return hsv(frac(t / 20f), 1f, v); // spectrum cycling, one loop every 20 s
    }

    /** fleet movement status background plus ability overlays for one LED */
    private int statusPixel(float x, float y, float aspect, float t, long now) {
        int c;
        int move = LightState.move;
        if (move == LightState.MOVE_SILENT) c = silent(x, y, t);
        else if (move == LightState.MOVE_SUSTAINED) c = burn(x, y, t, false);
        else if (move == LightState.MOVE_EMERGENCY) c = burn(x, y, t, true);
        else c = normal(t);

        // polar position around the middle of the device, 1 = farthest corner
        float dx = (x - 0.5f) * aspect;
        float dy = y - 0.5f;
        float maxR = (float) Math.sqrt(aspect * aspect * 0.25f + 0.25f);
        float dn = (float) Math.sqrt(dx * dx + dy * dy) / maxR;

        // neutrino detector: breathing blue glow around the edge
        if (LightState.neutrino) {
            float e = Math.min(Math.min(x, 1f - x) * aspect, Math.min(y, 1f - y));
            float breathe = 0.4f + 0.6f * (0.5f - 0.5f * (float) Math.cos(t * 2.1f));
            c = lerp(c, NEUTRINO, (float) Math.exp(-e / 0.1f) * breathe);
        }

        // active sensor burst: gathering glow, then a white ring from the middle
        float sc = LightState.sensorCharge;
        if (sc > 0f) c = lerp(c, 0xFFFFFF, sc * 0.7f * (float) Math.exp(-(dn * dn) / (0.06f + 0.1f * sc)));
        float se = (now - LightState.sensorAt) / 1000f;
        if (LightState.sensorAt > 0L && 1.5f > se && se >= 0f) {
            float ring = (dn - se * 1.05f) / 0.09f;
            float v = (float) Math.exp(-ring * ring) * (1f - se / 1.5f);
            if (0.08f > se) v = 1f;
            c = lerp(c, 0xFFFFFF, v);
        }

        // interdiction pulse: the board dims and a ring closes in while it charges ... then it goes off
        float pc = LightState.pulseCharge;
        if (pc > 0f) {
            c = scale(c, 1f - 0.6f * pc);
            float r = 1f - pc;
            float ring = (dn - r) / 0.08f;
            float beat = 0.55f + 0.45f * (float) Math.sin(t * (6f + pc * 14f));
            c = lerp(c, PULSE, (float) Math.exp(-ring * ring) * 0.95f * beat);
            c = lerp(c, 0xFFFFFF, pc * pc * (float) Math.exp(-(dn * dn) / 0.01f));
        }
        float pe = (now - LightState.pulseAt) / 1000f;
        if (LightState.pulseAt > 0L && 1.3f > pe && pe >= 0f) {
            float fade = 1f - pe / 1.3f;
            float ring = (dn - pe * 1.5f) / 0.16f;
            float echo = (dn - (pe - 0.25f) * 1.5f) / 0.08f;
            float v = (float) Math.exp(-ring * ring) * fade;
            if (pe > 0.25f) v = Math.max(v, (float) Math.exp(-echo * echo) * fade * 0.5f);
            if (0.12f > pe) v = Math.max(v, 1f - pe / 0.12f);
            c = lerp(c, lerp(PULSE, 0xFFFFFF, v), v);
        }

        // transverse jump: purple tendrils close in on the middle
        if (jumpLevel > 0.001f) {
            float L = jumpLevel;
            float th = (float) Math.atan2(dy, dx);
            float arms = 0.5f + 0.5f * (float) Math.sin(th * 6f + dn * 9f - t * 2.6f);
            float front = 1.05f - L * 1.1f;
            float m = clamp01((dn - front + arms * 0.22f) / 0.1f);
            int col = lerp(TENDRIL_DARK, TENDRIL, arms * (0.6f + 0.4f * (float) Math.sin(t * 4f + dn * 6f)));
            float tip = (dn - front - arms * 0.05f) / 0.07f;
            col = lerp(col, TENDRIL_TIP, (float) Math.exp(-tip * tip) * arms);
            c = lerp(scale(c, 1f - 0.6f * L), col, m);
        }
        return c;
    }

    private void updateJump(float dt, long now) {
        float ch = LightState.jumpCharge;
        if (lastJumpCharge > 0.9f && 0f >= ch) jumpHoldUntil = now + 1500; // jump under way, wait for it
        lastJumpCharge = ch;
        long done = LightState.jumpDoneAt;
        if (done != jumpDoneSeen) {
            jumpDoneSeen = done;
            jumpHoldUntil = 0L;
            jumpFastUntil = now + 1500;
        }
        float target = ch > 0f ? ch : (jumpHoldUntil > now ? 1f : 0f);
        float down = jumpFastUntil > now ? dt / 0.3f : dt / 1.0f;
        jumpLevel = approach(jumpLevel, target, dt / 0.2f, down);
    }

    /** normal travel: almost-dark light blue breathing up to light blue */
    private static int normal(float t) {
        float b = 0.5f - 0.5f * (float) Math.cos(t * 1.4f);
        return lerp(scale(Config.ready, 0.07f), scale(Config.ready, 0.6f), b);
    }

    /** running silent: really dim purple / dark purple vertical bands drifting sideways */
    private static int silent(float x, float y, float t) {
        float s = 0.5f + 0.5f * (float) Math.sin((x * 1.6f - t * 0.22f) * 6.2832f + 0.7f * (float) Math.sin(y * 3f + t * 0.8f));
        return lerp(SILENT_DARK, SILENT_LIGHT, s);
    }

    /** sustained / emergency burn: streaming star field with a wobbling red line through the middle */
    private static int burn(float x, float y, float t, boolean emergency) {
        float k = emergency ? 2.6f : 1f;
        int[] stars = emergency ? STARS_EMERGENCY : STARS_SUSTAINED;
        int c = emergency ? 0x0C0000 : 0x180600;

        // stars scroll sideways, like the burn drive streaking past them
        int col = (int) Math.floor(x * 21f + t * 2.2f * k);
        int row = Math.round(y * 5f);
        int id = col * 7 + row * 1031;
        float r = rand(id, 1);
        if (r > 0.6f) {
            float tw = 0.5f + 0.5f * (float) Math.sin(t * k * (1.5f + r * 3f) + r * 40f);
            c = lerp(c, stars[(int) (rand(id, 2) * 3f)], tw * tw * (emergency ? 0.85f : 1f));
        }

        // the line
        float yc = 0.5f + 0.14f * (float) Math.sin(x * 7f + t * 2.2f * k) + 0.05f * (float) Math.sin(x * 17f - t * 3.1f * k);
        float dy = y - yc;
        float halo = (float) Math.exp(-(dy * dy) / 0.04f);
        float core = (float) Math.exp(-(dy * dy) / 0.006f);
        c = lerp(c, emergency ? 0x8A1000 : 0xFF7A10, halo * 0.85f);
        c = lerp(c, emergency ? 0xE00000 : 0xFF1A00, core);
        return c;
    }

    // ------------------------------------------------------------------ combat

    private void renderCombat(float t, float dt, long nowMs) {
        if (LightState.dead) {
            renderDeath((nowMs - LightState.deathAt) / 1000f, t);
            return;
        }

        float flux = clamp01(LightState.flux);
        float hull = clamp01(LightState.hull);
        boolean ship = LightState.hasShip;

        // shield rises slowly from the bottom, drops a bit faster
        shieldFill = approach(shieldFill, ship && LightState.shieldOn ? 1f : 0f, dt / 1.4f, dt / 0.6f);
        phaseFill = approach(phaseFill, ship && LightState.phased ? 1f : 0f, dt / 0.8f, dt / 0.5f);

        if (ship && !hadShip) lastHull = hull; // a ship that starts damaged is not a hit
        hadShip = ship;
        if (ship && lastHull > hull + 0.002f) hitFlash = Math.min(1f, hitFlash + (lastHull - hull) * 10f + 0.25f);
        lastHull = hull;
        hitFlash = Math.max(0f, hitFlash - dt * 3f);

        float damage = ship ? 1f - hull : 0f;
        float heat = smooth(flux);
        int body = lerp(Config.combatWhite, Config.fluxHot, heat);
        // shield goes blue -> violet -> red-orange as flux builds (a straight blend turns grey)
        int shieldCol = 0.5f > heat ? lerp(Config.shield, 0x8A3CFF, heat * 2f) : lerp(0x8A3CFF, Config.fluxHot, (heat - 0.5f) * 1.6f);

        // flicker speed grows with damage
        int bucket = (int) (t * (5f + damage * 20f));

        for (int d = 0; d < Devices.COUNT; d++) {
            int[] a = frame[d];
            float[] xs = Devices.X[d];
            float[] ys = Devices.Y[d];
            for (int i = 0; i < a.length; i++) {
                float y = ys[i];
                int c = body;
                if (shieldFill > 0f) c = lerp(c, shieldCol, edge(shieldFill * 1.15f - y));
                if (phaseFill > 0f) c = lerp(c, Config.phase, edge(phaseFill * 1.15f - y) * 0.85f);

                if (LightState.overloaded) {
                    // electric crackle
                    float r = rand(d * 131 + i, bucket * 3 + (int) (t * 30f));
                    c = r > 0.55f ? lerp(0x9FD8FF, 0xFFFFFF, r) : scale(c, 0.25f);
                } else if (LightState.venting) {
                    c = scale(c, 0.65f + 0.35f * (float) Math.sin(t * 14f + y * 6f));
                }

                if (hitFlash > 0f) c = lerp(c, 0xFF1000, hitFlash * 0.6f);

                if (damage > 0.04f) c = damaged(c, d, i, damage, bucket);
                a[i] = c;
            }
        }
    }

    /** hull damage: keys flicker off, show glitch colours, and finally die for good */
    private int damaged(int c, int d, int i, float damage, int bucket) {
        int id = d * 977 + i;
        // permanently dead LEDs once the hull is badly hurt
        if (damage > 0.6f && (damage - 0.6f) * 1.4f > rand(id, 7)) return 0;
        float r = rand(id, bucket);
        if (damage * 0.4f > r) return 0;                       // flicker off
        if (damage > 0.3f && (damage - 0.3f) * 0.55f > rand(id, bucket + 101)) {
            int pick = (int) (rand(id, bucket + 202) * 4f);
            switch (pick) {
                case 0: return 0xFF9000;  // sparks
                case 1: return 0xFF0000;
                case 2: return 0xFF20A0;
                default: return 0x30FFFF;
            }
        }
        if (damage > 0.5f) return scale(c, 1f - (damage - 0.5f) * 0.8f * rand(id, bucket + 303));
        return c;
    }

    /** flagship destroyed: white flash, fire ring from the middle, embers, then dark except Tab */
    private void renderDeath(float since, float t) {
        float life = since / EXPLOSION_SECONDS;
        float radius = since * 1.1f;
        for (int d = 0; d < Devices.COUNT; d++) {
            int[] a = frame[d];
            float[] xs = Devices.X[d];
            float[] ys = Devices.Y[d];
            float aspect = Devices.ASPECT[d];
            for (int i = 0; i < a.length; i++) {
                if (life >= 1f) { a[i] = 0; continue; }
                float dx = (xs[i] - 0.5f) * aspect;
                float dy = ys[i] - 0.5f;
                float dist = (float) Math.sqrt(dx * dx + dy * dy) / Math.max(1f, aspect * 0.5f);
                float ring = (dist - radius) / 0.12f;
                float heat = (float) Math.exp(-ring * ring);
                // embers left behind the ring
                if (radius > dist) {
                    float ember = (1f - life) * 0.55f * (0.5f + 0.5f * rand(d * 389 + i, (int) (since * 12f)));
                    heat = Math.max(heat, ember);
                }
                if (0.12f > since) heat = 1f; // first flash
                heat *= 1f - life * life;
                a[i] = fire(heat);
            }
        }
        if (life >= 0.85f) {
            // the command screen key stays lit
            float pulse = 0.65f + 0.35f * (float) Math.sin(t * 2.5f);
            float in = Math.min(1f, (life - 0.85f) / 0.15f);
            frame[Devices.KEYBOARD][Devices.keyIndex(Devices.KEY_TAB)] = scale(Config.ready, pulse * in);
            if (life >= 1f) frame[Devices.KEYBOARD][Devices.keyIndex(Devices.KEY_TAB)] = scale(Config.ready, pulse);
        }
    }

    /** black -> red -> orange -> yellow -> white */
    static int fire(float h) {
        h = clamp01(h);
        if (0.25f > h) return lerp(0x000000, 0x900000, h / 0.25f);
        if (0.5f > h) return lerp(0x900000, 0xFF3C00, (h - 0.25f) / 0.25f);
        if (0.8f > h) return lerp(0xFF3C00, 0xFFC020, (h - 0.5f) / 0.3f);
        return lerp(0xFFC020, 0xFFFFFF, (h - 0.8f) / 0.2f);
    }

    // ------------------------------------------------------------------ helpers

    static float approach(float v, float target, float up, float down) {
        if (target > v) return Math.min(target, v + up);
        return Math.max(target, v - down);
    }

    /** soft edge for a fill line */
    static float edge(float v) {
        return clamp01(v / 0.15f + 0.5f);
    }

    static float smooth(float v) {
        return v * v * (3f - 2f * v);
    }

    static float clamp01(float v) {
        return v > 1f ? 1f : (0f > v ? 0f : v);
    }

    static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** stable pseudo-random 0..1 from two ints */
    static float rand(int a, int b) {
        int h = a * 0x27d4eb2d + b * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / 16777216f;
    }

    static int lerp(int a, int b, float t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int scale(int c, float f) {
        f = clamp01(f);
        int r = (int) (((c >> 16) & 255) * f);
        int g = (int) (((c >> 8) & 255) * f);
        int b = (int) ((c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static int hsv(float h, float s, float v) {
        float r, g, b;
        float hh = frac(h) * 6f;
        int sector = (int) hh;
        float f = hh - sector;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float u = v * (1f - s * (1f - f));
        switch (sector) {
            case 0: r = v; g = u; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = u; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = u; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
    }
}
