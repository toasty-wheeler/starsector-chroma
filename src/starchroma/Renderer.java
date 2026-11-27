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
    private float systemFade;
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
        systemFade = 0f;
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
        int sysType = ship ? LightState.systemType : LightState.SYS_NONE;
        boolean sysOn = ship && LightState.systemOn;
        // the game stopped updating (left the battle oddly): don't keep showing a frozen overload
        long seen = LightState.combatAt;
        boolean fresh = seen == 0L || 3000L > nowMs - seen;
        boolean phasing = ship && (LightState.phased || (sysType == LightState.SYS_PHASE && sysOn));
        phaseFill = approach(phaseFill, phasing ? 1f : 0f, dt / 0.8f, dt / 0.5f);
        // the system effect fades in and out a little so short systems still read
        float sysTarget = sysOn ? Math.max(0.35f, clamp01(LightState.systemLevel)) : 0f;
        systemFade = approach(systemFade, sysTarget, dt / 0.15f, dt / 0.4f);
        float sinceOn = (nowMs - LightState.systemAt) / 1000f;
        // a teleport's reappearing burst waits until the collapse has played, however short the system
        float sinceEnd = (nowMs - Math.max(LightState.systemEndAt, LightState.systemAt + 350L)) / 1000f;
        boolean ended = LightState.systemEndAt >= LightState.systemAt && LightState.systemEndAt > 0L;

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
                if (phaseFill > 0f) {
                    c = lerp(c, Config.phase, edge(phaseFill * 1.15f - y) * 0.85f);
                    c = phaseShimmer(c, d, i, xs[i], y, t, phaseFill);
                }
                if (sysType == LightState.SYS_CHARGE && systemFade > 0f) c = chargeStreaks(c, d, xs[i], y, t, systemFade);
                else if (sysType == LightState.SYS_FEEDER && systemFade > 0f) c = ammoBelt(c, xs[i], y, t, systemFade);
                else if (sysType == LightState.SYS_OTHER && systemFade > 0f) c = systemPulse(c, d, xs[i], y, t, systemFade);
                else if (sysType == LightState.SYS_TELEPORT) c = teleport(c, d, i, xs[i], y, t, sysOn, sinceOn, ended, sinceEnd);

                if (ship && fresh && LightState.overloaded) {
                    // electric crackle
                    float r = rand(d * 131 + i, bucket * 3 + (int) (t * 30f));
                    c = r > 0.55f ? lerp(0x9FD8FF, 0xFFFFFF, r) : scale(c, 0.25f);
                } else if (ship && fresh && LightState.venting) {
                    c = scale(c, 0.65f + 0.35f * (float) Math.sin(t * 14f + y * 6f));
                }

                if (hitFlash > 0f) c = lerp(c, 0xFF1000, hitFlash * 0.6f);

                if (d == Devices.KEYBOARD && i == KB_F && sysType != LightState.SYS_NONE) c = systemKey(sysType, sysOn, t);

                if (damage > 0.04f) {
                    c = damaged(c, d, i, damage, t);
                }
                a[i] = c;
            }
        }
        if (ship) mouseSides(frame[Devices.MOUSE], sysType, sysOn, t);
        if (ship && 0.30f >= LightState.cr) crAlarm(LightState.cr, t);
    }

    public static final int CR_YELLOW = 0xFFC800, CR_RED = 0xFF1000;
    static final boolean[] KB_OUTLINE = new boolean[6 * 22];
    static {
        for (int r = 0; 6 > r; r++) {
            for (int c = 1; 22 > c; c++) {
                // the outer edge of a full-size board, plus the main typing block's edge
                // (number row, Esc/Tab/Caps/Shift/Ctrl column, Backspace/Enter/Shift column) so
                // 60% and TKL boards like the Huntsman Mini show a full frame too
                boolean outer = r == 0 || r == 5 || c == 1 || c == 21;
                boolean block = (r == 1 && 14 >= c) || (c == 14 && r >= 1 && 4 >= r) || (c == 15 && r == 5);
                KB_OUTLINE[r * 22 + c] = outer || block;
            }
        }
    }

    /** low crew readiness: the keyboard and mousepad outlines blink yellow (30% or less) or red (15% or less) */
    private void crAlarm(float cr, float t) {
        boolean critical = 0.15f >= cr;
        int col = critical ? CR_RED : CR_YELLOW;
        float rate = critical ? 1.4f : 0.8f;
        float b = 0.5f + 0.5f * (float) Math.cos(t * rate * 6.2832f);
        int on = scale(col, b * b);
        int[] kb = frame[Devices.KEYBOARD];
        for (int i = 0; i < kb.length; i++) if (KB_OUTLINE[i]) kb[i] = on;
        int[] pad = frame[Devices.MOUSEPAD];
        for (int i = 0; i < pad.length; i++) pad[i] = on;
    }

    /**
     * mouse side strips (Razer mouse grid column 0 = left side, 6 = right side, rows 1..7 top to
     * bottom): left shows ammo left, right shows ship system charges / cooldown
     */
    private void mouseSides(int[] m, int type, boolean on, float t) {
        float ammo = LightState.ammo;
        if (ammo >= 0f) {
            boolean low = 0.25f > ammo;
            int col = low ? Config.used : AMMO_BRASS;
            float pulse = low ? 0.6f + 0.4f * (float) Math.sin(t * 8f) : 1f;
            for (int k = 0; 7 > k; k++) {
                float fill = clamp01(ammo * 7f - k);     // k = 0 is the bottom LED
                int c;
                if (0f >= ammo) c = frac(t * 1.5f) > 0.5f ? Config.used : 0x200000;  // empty: slow red blink
                else c = lerp(scale(col, 0.06f), scale(col, pulse), fill);
                m[(7 - k) * 7] = c;
            }
        }
        if (type == LightState.SYS_NONE) return;
        int max = LightState.systemMaxCharges;
        float rech = clamp01(LightState.systemRecharge);
        for (int k = 0; 7 > k; k++) {
            float pos = (k + 0.5f) / 7f;
            int c;
            if (max > 0) {
                // charges stacked from the bottom, the one reloading in dim red above them
                float have = LightState.systemCharges / (float) max;
                if (have >= pos) c = on ? systemColor(type) : Config.ready;
                else if ((LightState.systemCharges + rech) / max >= pos) c = scale(Config.used, 0.75f);
                else c = 0x080808;
            } else if (on) {
                c = scale(systemColor(type), 0.75f + 0.25f * (float) Math.sin(t * 18f + k));
            } else if (LightState.systemReady) {
                c = Config.ready;
            } else {
                c = rech >= pos ? scale(Config.used, 0.75f) : 0x080808;
            }
            m[(7 - k) * 7 + 6] = c;
        }
    }

    private static int systemColor(int type) {
        switch (type) {
            case LightState.SYS_CHARGE: return CHARGE_CORE;
            case LightState.SYS_TELEPORT: return WARP;
            case LightState.SYS_PHASE: return Config.phase;
            case LightState.SYS_FEEDER: return AMMO_BRASS;
            default: return SYSTEM_GLOW;
        }
    }

    // ------------------------------------------------------------------ ship systems (F key)

    static final int KB_F = Devices.keyIndex(Devices.KEY_F);
    static final int CHARGE_HOT = 0xFF6A10, CHARGE_CORE = 0xFFF0C8;
    public static final int AMMO_BRASS = 0xFFC21A, AMMO_FLASH = 0xFF5A00;
    static final int WARP = 0x7FE8FF;
    static final int SYSTEM_GLOW = 0x40FFC8;

    /** the F key: light blue when ready, the system's colour while on, red while it recharges */
    private int systemKey(int type, boolean on, float t) {
        if (on) return scale(systemColor(type), 0.8f + 0.2f * (float) Math.sin(t * 18f));
        if (LightState.systemReady) return Config.ready;
        return scale(Config.used, 0.85f + 0.15f * (float) Math.sin(t * 4f));
    }

    /** burn drives and jets: hot speed streaks racing left to right, warm tint over everything */
    private int chargeStreaks(int c, int d, float x, float y, float t, float level) {
        int row = (int) (y * 10f + 0.5f);
        float seed = rand(d * 17 + row, 5);
        float speed = 2.2f + 1.6f * seed + 2.5f * level;
        float p = frac(x * 1.8f - t * speed + seed);
        float streak = p * p * p;  // bright head on the right, tail fading to the left
        // darker ember base so the streaks stand out
        c = lerp(c, 0x5A1800, level * 0.8f);
        int sc = lerp(CHARGE_HOT, CHARGE_CORE, streak);
        return lerp(c, sc, streak * level);
    }

    /** ammo feeders: brass dashes running along the rows like a belt, with muzzle-flash flicker */
    private int ammoBelt(int c, float x, float y, float t, float level) {
        int row = (int) (y * 5f + 0.5f);
        float dir = (row & 1) == 0 ? 1f : -1f;
        float p = frac(x * 5f - t * 4.5f * dir + row * 0.37f);
        float dash = clamp01((0.5f - Math.abs(p - 0.5f)) * 6f - 1f);
        float flash = rand((int) (t * 14f), 3) > 0.7f ? 1f : 0f;
        c = scale(c, 1f - 0.75f * level);
        int dc = lerp(AMMO_BRASS, AMMO_FLASH, flash * 0.6f);
        return lerp(c, dc, level * (0.25f + 0.75f * dash));
    }

    /** any other system: soft rings rolling out from the middle */
    private int systemPulse(int c, int d, float x, float y, float t, float level) {
        float aspect = Devices.ASPECT[d];
        float dx = (x - 0.5f) * aspect, dy = y - 0.5f;
        float dist = (float) Math.sqrt(dx * dx + dy * dy) / Math.max(1f, aspect * 0.5f);
        float wave = 0.5f + 0.5f * (float) Math.sin((dist * 9f - t * 7f));
        c = scale(c, 1f - 0.45f * level);
        return lerp(c, SYSTEM_GLOW, level * (0.1f + 0.85f * wave * wave));
    }

    /** phase: the lights go ghostly, violet interference bands drift and a few keys glint */
    private int phaseShimmer(int c, int d, int i, float x, float y, float t, float level) {
        float w = 0.5f + 0.5f * (float) (Math.sin(x * 9f + t * 2.6f) * Math.sin(y * 6f - t * 1.9f));
        int ghost = scale(c, 0.3f + 0.6f * w);
        if (rand(d * 613 + i, (int) (t * 6f)) > 0.96f) ghost = 0xE6D2FF;
        return lerp(c, ghost, level);
    }

    /**
     * teleporters: on activation the light collapses into the middle, the ship is "gone" (dim
     * cyan static), then it reappears as a burst from a random spot
     */
    private int teleport(int c, int d, int i, float x, float y, float t, boolean on, float sinceOn, boolean ended, float sinceEnd) {
        float aspect = Devices.ASPECT[d];
        float norm = Math.max(1f, aspect * 0.5f);
        if (on || (ended && 0f > sinceEnd)) {
            float dx = (x - 0.5f) * aspect, dy = y - 0.5f;
            float dist = (float) Math.sqrt(dx * dx + dy * dy) / norm;
            float k = clamp01(sinceOn / 0.35f);
            float radius = 0.9f * (1f - k);
            float ring = (dist - radius) / 0.1f;
            float glow = (float) Math.exp(-ring * ring);
            // everything outside the closing ring is sucked dark
            float keep = dist > radius ? 0.12f : 1f;
            int out = scale(c, keep);
            if (k >= 1f) {
                // in transit: dim cyan static
                out = rand(d * 211 + i, (int) (t * 20f)) > 0.88f ? scale(WARP, 0.6f) : scale(WARP, 0.06f);
                float centre = (float) Math.exp(-dist * dist / 0.01f);
                out = lerp(out, 0xFFFFFF, centre * 0.8f);
            }
            return lerp(out, 0xFFFFFF, glow);
        }
        if (ended && 0.9f > sinceEnd) {
            int seed = (int) (LightState.systemEndAt & 0x7FFFFFFF);
            float ox = 0.15f + 0.7f * rand(seed, 1), oy = 0.2f + 0.6f * rand(seed, 2);
            float dx = (x - ox) * aspect, dy = y - oy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy) / norm;
            float radius = sinceEnd * 1.6f;
            float ring = (dist - radius) / 0.14f;
            float fade = 1f - sinceEnd / 0.9f;
            float glow = (float) Math.exp(-ring * ring) * fade;
            float flash = 0.12f > sinceEnd ? (float) Math.exp(-dist * dist / 0.02f) : 0f;
            // the world fades back in behind the ring
            int out = radius > dist ? c : scale(c, 0.12f + 0.88f * (1f - fade));
            out = lerp(out, WARP, glow);
            return lerp(out, 0xFFFFFF, Math.max(flash, glow * 0.5f));
        }
        return c;
    }

    /**
     * hull damage, calm and readable: more and more keys go dark for good, some slowly fade to
     * black and back, and a few slowly blink red/orange to black. Each key keeps its role, so
     * nothing jumps around.
     */
    private int damaged(int c, int d, int i, float damage, float t) {
        int id = d * 977 + i;
        float role = rand(id, 7);
        float phase = rand(id, 11);
        // dead for good: none until 15% damage, about 60% of keys at a wrecked hull
        if ((damage - 0.15f) * 0.7f > role) return 0;
        // red/orange alarm keys, slow blink to black (about once a second)
        if ((damage - 0.05f) * 0.22f > rand(id, 13)) {
            float rate = 0.7f + 0.5f * phase;
            float b = 0.5f + 0.5f * (float) Math.cos((t * rate + phase) * 6.2832f);
            float shift = 0.5f + 0.5f * (float) Math.sin(t * 0.9f + phase * 6.2832f);
            return scale(lerp(0xFF1000, 0xFF7A00, shift), b * b);
        }
        // slow breathing to black (one cycle every 1.5-3 s)
        if (damage * 0.45f > rand(id, 17)) {
            float rate = 0.35f + 0.3f * phase;
            float b = 0.5f + 0.5f * (float) Math.cos((t * rate + phase) * 6.2832f);
            return scale(c, b);
        }
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
