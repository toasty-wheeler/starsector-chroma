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
    private double animClock;
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
        // animation clock: advances at most 0.1 s per frame, so after a hitch (game loading,
        // Synapse busy) the breathing etc. carries on from where it was instead of jumping ahead
        if (lastMs == 0L) animClock = (nowMs % 3600000L) / 1000.0;
        else animClock += Math.min(dt, 0.1f);
        if (animClock >= 3600.0) animClock -= 3600.0;
        lastMs = nowMs;
        float t = (float) animClock;

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
    static final int NEUTRINO = 0x2E7BFF, NEUTRINO_LIGHT = 0x7FD0FF, STAR_BLUE = 0x9FC8FF;
    static final int SLOW_RIPPLE = 0xFF1A08;
    public static final int TOGGLE_ON = 0xFFC000, TOGGLE_ON_LIGHT = 0xFFF0A0;
    public static final int ALERT_YELLOW = 0xFFFF00, ALERT_RED = 0xFF1000;
    public static final int SUPPLY_COL = 0x60FF40, FUEL_COL = 0xFF9A1A;

    private float neutrinoLevel;
    private float burnSpeed;
    private double burnClock;
    private boolean slowWas;
    /** animation time when S was let go (-1 = held or long gone) */
    private float slowReleaseT = -1f;
    static final float SLOW_RIPPLE_SECONDS = 1.4f;
    private float slowPressT;
    /** overload crackle clock (ticks slow down as the overload wears off) and 0..1 how far it has worn off */
    private double overloadClock;
    private float overloadHealed;
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
        neutrinoLevel = approach(neutrinoLevel, LightState.neutrino ? 1f : 0f, dt / 0.8f, dt / 0.8f);
        // deep space: fully dark a little way past the edge of the abyss, fading over ~1.5 s
        float abyss = LightState.abyss;
        abyssLevel = approach(abyssLevel, abyss > 0f ? Math.min(1f, 0.6f + abyss * 4f) : 0f, dt / 1.5f, dt / 1.5f);
        // burn animation: blends in and speeds up with the fleet's burn level (full at burn 20); its own
        // clock so a speed change never makes the star field jump
        boolean burning = LightState.move == LightState.MOVE_SUSTAINED || LightState.move == LightState.MOVE_EMERGENCY;
        burnSpeed = approach(burnSpeed, burning ? clamp01(LightState.burnLevel) : 0f, dt / 0.6f, dt / 0.6f);
        burnClock += Math.min(dt, 0.1f) * burnSpeed;
        if (burnClock >= 3600.0) burnClock -= 3600.0;
        // letting go of S: no new ripples, the ones already out finish spreading
        boolean slow = LightState.slowMoving;
        if (slowWas && !slow) slowReleaseT = t;
        if (slow && !slowWas) slowPressT = t;
        if (slow) slowReleaseT = -1f;
        slowWas = slow;

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
        lowStockFlash(t);
        mouseCampaign(t);
        int[] kb = frame[Devices.KEYBOARD];
        System.arraycopy(kb, 0, keysUnder, 0, kb.length); // to tell the lit buttons from the background
        logistics(kb, t);

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
            } else if (st == LightState.SLOT_ACTIVE) {
                // switched-on toggle: slow cycle between yellow and white-yellow (about 4 s)
                kb[idx] = lerp(TOGGLE_ON, TOGGLE_ON_LIGHT, 0.5f - 0.5f * (float) Math.cos(t * 1.6f));
            }
        }
        lastSlots = slots;

        for (int k = 0; k < Devices.KEY_SCREENS.length; k++) {
            kb[Devices.keyIndex(Devices.KEY_SCREENS[k])] = Config.ready;
        }
        if (status && abyssLevel > 0.001f) deepSpaceFlicker(kb, t);
        // last, so the ripples pass over the hotkeys, ability keys and bars instead of under them
        if (status) slowRipples(kb, t);
        if (status) abilityRings(t, now);
    }


    /** sensor burst, interdiction pulse and jump tendrils, drawn last on every device so they pass over the buttons too */
    private void abilityRings(float t, long now) {
        boolean sensor = LightState.sensorCharge > 0f || (LightState.sensorAt > 0L && now >= LightState.sensorAt && 1500L > now - LightState.sensorAt);
        boolean pulse = LightState.pulseCharge > 0f || (LightState.pulseAt > 0L && now >= LightState.pulseAt && 1300L > now - LightState.pulseAt);
        boolean jump = jumpLevel > 0.001f;
        if (!sensor && !pulse && !jump) return;
        for (int d = 0; d < Devices.COUNT; d++) {
            int[] a = frame[d];
            float[] xs = Devices.X[d];
            float[] ys = Devices.Y[d];
            float aspect = Devices.ASPECT[d];
            // distance from the middle (on the keyboard: the middle of the letter keys, G/H), 1 = farthest corner
            float cx = d == Devices.KEYBOARD ? MID_KEYS_X : 0.5f;
            float cy = d == Devices.KEYBOARD ? MID_KEYS_Y : 0.5f;
            float fx = Math.max(cx, 1f - cx) * aspect, fy = Math.max(cy, 1f - cy);
            float far = fx * fx + fy * fy;
            for (int i = 0; i < a.length; i++) {
                float kx = (xs[i] - cx) * aspect, ky = ys[i] - cy;
                float dk = (float) Math.sqrt((kx * kx + ky * ky) / far);
                int c = sensor || pulse ? ring(a[i], dk, t, now) : a[i];
                a[i] = jump ? tendrils(c, kx, ky, dk, t) : c;
            }
        }
    }


    /** transverse jump: purple tendrils close in on the middle (kx, ky from the middle, dk 1 = farthest corner) */
    private int tendrils(int c, float kx, float ky, float dk, float t) {
        float L = jumpLevel;
        float th = (float) Math.atan2(ky, kx);
        float arms = 0.5f + 0.5f * (float) Math.sin(th * 6f + dk * 9f - t * 2.6f);
        float front = 1.05f - L * 1.1f;
        float m = clamp01((dk - front + arms * 0.22f) / 0.1f);
        int col = lerp(TENDRIL_DARK, TENDRIL, arms * (0.6f + 0.4f * (float) Math.sin(t * 4f + dk * 6f)));
        float tip = (dk - front - arms * 0.05f) / 0.07f;
        col = lerp(col, TENDRIL_TIP, (float) Math.exp(-tip * tip) * arms);
        return lerp(scale(c, 1f - 0.6f * L), col, m);
    }
    private int ring(int c, float dk, float t, long now) {
        // active sensor burst: gathering glow, then a white ring from the middle
        float sc = LightState.sensorCharge;
        if (sc > 0f) c = lerp(c, 0xFFFFFF, sc * 0.7f * (float) Math.exp(-(dk * dk) / (0.06f + 0.1f * sc)));
        float se = (now - LightState.sensorAt) / 1000f;
        if (LightState.sensorAt > 0L && 1.5f > se && se >= 0f) {
            float ring = (dk - se * 1.05f) / 0.09f;
            float v = (float) Math.exp(-ring * ring) * (1f - se / 1.5f);
            if (0.08f > se) v = 1f;
            c = lerp(c, 0xFFFFFF, v);
        }

        // interdiction pulse: the board dims and a ring closes in while it charges ... then it goes off
        float pc = LightState.pulseCharge;
        if (pc > 0f) {
            c = scale(c, 1f - 0.6f * pc);
            float r = 1f - pc;
            float ring = (dk - r) / 0.08f;
            float beat = 0.55f + 0.45f * (float) Math.sin(t * (6f + pc * 14f));
            c = lerp(c, PULSE, (float) Math.exp(-ring * ring) * 0.95f * beat);
            c = lerp(c, 0xFFFFFF, pc * pc * (float) Math.exp(-(dk * dk) / 0.01f));
        }
        float pe = (now - LightState.pulseAt) / 1000f;
        if (LightState.pulseAt > 0L && 1.3f > pe && pe >= 0f) {
            float fade = 1f - pe / 1.3f;
            float ring = (dk - pe * 1.5f) / 0.16f;
            float echo = (dk - (pe - 0.25f) * 1.5f) / 0.08f;
            float v = (float) Math.exp(-ring * ring) * fade;
            if (pe > 0.25f) v = Math.max(v, (float) Math.exp(-echo * echo) * fade * 0.5f);
            if (0.12f > pe) v = Math.max(v, 1f - pe / 0.12f);
            c = lerp(c, lerp(PULSE, 0xFFFFFF, v), v);
        }

        return c;
    }
    /** the keyboard as it was before the buttons (hotkeys, ability keys, bars) were drawn on it */
    private final int[] keysUnder = new int[Devices.size(Devices.KEYBOARD)];
    /** 0..1 how far the keyboard has gone dark for deep space (abyssal hyperspace) */
    private float abyssLevel;

    /**
     * deep space: the background is blacked out (in statusPixel) and every lit button flickers like
     * a failing light, each on its own random rhythm, with the odd longer dropout
     */
    private void deepSpaceFlicker(int[] kb, float t) {
        for (int i = 0; i < kb.length; i++) {
            if (kb[i] == keysUnder[i]) continue; // not a button
            float rate = 7f + 9f * rand(i, 91);
            int n = (int) Math.floor(t * rate + rand(i, 92) * 13f);
            float r = rand(i * 7 + 3, n);
            float b = r > 0.4f ? 1f : (r > 0.18f ? 0.35f : 0.03f);
            // now and then a key goes out for a moment
            int w = (int) Math.floor(t * 0.9f + rand(i, 93) * 11f);
            if (0.22f > rand(i * 13 + 5, w)) b *= 0.08f;
            kb[i] = scale(kb[i], 1f - abyssLevel * (1f - b));
        }
    }

    /**
     * holding S (moving slowly): small red ripples spreading from the S key; when it is let go
     * the ripples already out keep spreading and fade instead of vanishing
     */
    private void slowRipples(int[] kb, float t) {
        boolean slow = LightState.slowMoving;
        boolean fading = !slow && slowReleaseT >= 0f && SLOW_RIPPLE_SECONDS > t - slowReleaseT;
        if (!slow && !fading) return;
        float aspect = Devices.ASPECT[Devices.KEYBOARD];
        float sx = Devices.keyCol(Devices.KEY_S) / 21f;
        float sy = 1f - Devices.keyRow(Devices.KEY_S) / 5f;
        float[] xs = Devices.X[Devices.KEYBOARD];
        float[] ys = Devices.Y[Devices.KEYBOARD];
        for (int i = 0; i < kb.length; i++) {
            float rx = (xs[i] - sx) * aspect, ry = ys[i] - sy;
            float dist = (float) Math.sqrt(rx * rx + ry * ry);
            // one wave at a time, the first leaving the key the moment S goes down
            float age = frac((t - slowPressT) / SLOW_RIPPLE_SECONDS);
            if (fading && t - age * SLOW_RIPPLE_SECONDS > slowReleaseT) continue; // born after the release
            float ring = (dist - age * 0.8f) / 0.13f;
            float v = (float) Math.exp(-ring * ring) * (1f - age);
            if (v > 0.002f) kb[i] = lerp(kb[i], SLOW_RIPPLE, Math.min(1f, v * 1.5f));
        }
    }

    /**
     * low stock: the background flashes yellow with under a week of supplies, red under 20% fuel,
     * and alternates red / yellow flashes when both are low. One flash every 1.6 s.
     */
    private void lowStockFlash(float t) {
        float days = LightState.supplyDays, fuel = LightState.fuel;
        boolean lowSup = days >= 0f && 7f > days;
        boolean lowFuel = fuel >= 0f && 0.2f > fuel;
        if (!lowSup && !lowFuel) return;
        float cyc = t / 1.6f;
        int n = (int) Math.floor(cyc);
        float s = (float) Math.sin((cyc - n) * Math.PI);
        float pulse = s * s * 0.8f;
        int col = lowSup && lowFuel ? ((n & 1) == 0 ? ALERT_RED : ALERT_YELLOW) : (lowFuel ? ALERT_RED : ALERT_YELLOW);
        for (int d = 0; d < Devices.COUNT; d++) {
            int[] a = frame[d];
            for (int i = 0; i < a.length; i++) a[i] = lerp(a[i], col, pulse);
        }
    }

    /**
     * campaign mouse: left side = supplies, right side = fuel (filling from the bottom, red pulse
     * under 20%); wheel and logo blink yellow (under a week of supplies), red (under 20% fuel),
     * or alternate red / yellow when both are low
     */
    private void mouseCampaign(float t) {
        int[] m = frame[Devices.MOUSE];
        sideBar(m, 0, LightState.supplies, SUPPLY_COL, t);
        sideBar(m, 6, LightState.fuel, FUEL_COL, t);
        float days = LightState.supplyDays, fuel = LightState.fuel;
        boolean lowSup = days >= 0f && 7f > days;
        boolean lowFuel = fuel >= 0f && 0.2f > fuel;
        if (!lowSup && !lowFuel) return;
        float cyc = t / 0.8f;               // a blink every 0.8 s, sharper than the background flash
        int n = (int) Math.floor(cyc);
        boolean on = 0.55f > cyc - n;
        int col = lowSup && lowFuel ? ((n & 1) == 0 ? ALERT_RED : ALERT_YELLOW) : (lowFuel ? ALERT_RED : ALERT_YELLOW);
        int c = on ? col : 0;
        m[MOUSE_WHEEL] = c;
        m[MOUSE_LOGO] = c;
    }

    /** a mouse side strip (column 0 or 6, rows 1..7) filled from the bottom; pulses red under 20% */
    private void sideBar(int[] m, int col, float v, int color, float t) {
        if (0f > v) return;
        if (0.2f > v) color = scale(Config.used, 0.7f + 0.3f * (float) Math.sin(t * 5f));
        for (int k = 0; 7 > k; k++) {
            float fill = clamp01(v * 7f - k);
            m[(7 - k) * 7 + col] = lerp(0x080808, color, fill);
        }
    }

    /** numpad bars (supplies, fuel, fleet readiness) and the nav-key ring for fleet hull */
    private void logistics(int[] kb, float t) {
        bar(kb, NUMPAD_AMMO, LightState.supplies, SUPPLY_COL, t);
        bar(kb, NUMPAD_AMMO + 1, LightState.fuel, FUEL_COL, t);
        bar(kb, NUMPAD_AMMO + 2, LightState.fleetCR, Config.ready, t);

        float hull = LightState.fleetHull;
        if (hull >= 0f) {
            int col = 0.3f > hull ? Config.used : (0.5f > hull ? ALERT_YELLOW : Config.ready);
            int[] ring = Devices.KEY_NAV_CLOCKWISE;
            for (int j = 0; j < ring.length; j++) {
                // keys go dark one by one, clockwise from Insert, as the fleet's hull drops
                float on = clamp01(hull * ring.length - (ring.length - 1 - j));
                kb[Devices.keyIndex(ring[j])] = lerp(0x080808, col, on);
            }
        }
    }

    /** one numpad column (rows 1..5) filled from the bottom; pulses red under 20% */
    private void bar(int[] kb, int col, float v, int color, float t) {
        if (0f > v) return;
        if (0.2f > v) color = scale(Config.used, 0.7f + 0.3f * (float) Math.sin(t * 5f));
        for (int k = 0; NUMPAD_ROWS > k; k++) {
            float fill = clamp01(v * NUMPAD_ROWS - k);
            kb[(NUMPAD_TOP + NUMPAD_ROWS - 1 - k) * 22 + col] = lerp(0x080808, color, fill);
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
        else if (move == LightState.MOVE_SUSTAINED) c = lerp(normal(t), burn(x, y, (float) burnClock, false), burnSpeed);
        else if (move == LightState.MOVE_EMERGENCY) c = lerp(normal(t), burn(x, y, (float) burnClock, true), burnSpeed);
        else c = normal(t);
        // deep space: the keyboard's background goes black (the abilities still draw on top)
        boolean kbd = aspect == Devices.ASPECT[Devices.KEYBOARD];
        if (kbd && abyssLevel > 0f) c = scale(c, 1f - abyssLevel);

        // position around the middle of the device
        float dx = (x - 0.5f) * aspect;
        float dy = y - 0.5f;

        // neutrino detector: a blue glow around the edge that weaves in and out (reaching about a
        // row deeper at its crests) and breathes in step with the normal background, which is
        // dimmed underneath so the two blues don't fight. Fades in/out instead of popping.
        if (neutrinoLevel > 0.001f) {
            float e = Math.min(Math.min(x, 1f - x) * aspect, Math.min(y, 1f - y));
            float th = (float) Math.atan2(dy, dx);
            float weave = 0.5f + 0.5f * (float) (Math.sin(th * 5f - t * 1.1f) * Math.cos(th * 3f + t * 0.7f));
            float depth = 0.12f + 0.12f * weave;
            float g = (float) Math.exp(-e / depth);
            // the middle goes dark, with stars slowly twinkling in and out on random keys
            int inner = scale(c, 0.06f);
            int id = (int) (x * 211f) * 131 + (int) (y * 97f) * 7;
            float phase = t * (0.25f + 0.35f * rand(id, 3)) + rand(id, 4) * 7f;
            int n = (int) Math.floor(phase);
            if (rand(id, n + 17) > 0.72f) {
                float s = (float) Math.sin(frac(phase) * Math.PI);
                inner = lerp(inner, lerp(STAR_BLUE, 0xFFFFFF, rand(id, n + 29)), s * s);
            }
            c = lerp(c, inner, neutrinoLevel * (1f - g));
            // the outline stays fully lit; only its depth weaves
            int col = lerp(NEUTRINO, NEUTRINO_LIGHT, weave * g);
            c = lerp(c, col, g * neutrinoLevel);
        }

        // (sensor burst, interdiction pulse and jump tendrils are drawn last, over everything: see abilityRings)
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
        return lerp(scale(Config.ready, 0.12f), scale(Config.ready, 0.6f), b);
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

        // overload crackle: 30 flickers a second at the start, slowing to ~2 as it runs out
        float total = LightState.overloadTotal;
        float leftShare = total > 0f ? clamp01(LightState.overloadLeft / total) : 1f;
        overloadHealed = 1f - leftShare;
        overloadClock += Math.min(dt, 0.1f) * (2f + 28f * leftShare * leftShare);

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
                    // electric crackle that slows down, thins out and lets the board come back as
                    // the overload wears off, like the systems are fixing themselves
                    float r = rand(d * 131 + i, (int) overloadClock);
                    c = r > 0.55f + 0.35f * overloadHealed ? lerp(0x9FD8FF, 0xFFFFFF, r) : scale(c, 0.25f + 0.7f * overloadHealed);
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
        flameLevel = approach(flameLevel, ship && fresh && LightState.flamedOut ? 1f : 0f, dt / 0.25f, dt / 0.6f);
        if (flameLevel > 0.001f) flameout(t);
        if (ship && 0.30f >= LightState.cr) crAlarm(LightState.cr, t);
        if (ship) gauges(sysType, sysOn, t);
    }

    public static final int FLAMEOUT_RED = 0xFF1400;
    /** seconds for one side to go off -> red -> back to normal */
    static final float FLAMEOUT_CYCLE = 1.6f;
    private float flameLevel;

    /**
     * engines flamed out: two zones at the bottom left and bottom right of the main keyboard block
     * take turns. While one glows red the other goes dark; then the dark one rises to red while the
     * red one fades back to its normal colour, and so on.
     */
    private void flameout(float t) {
        int[] kb = frame[Devices.KEYBOARD];
        for (int row = 3; 5 >= row; row++) {
            float rowW = row == 3 ? 0.45f : 1f;
            for (int col = 0; 14 >= col; col++) {
                float left = 3 >= col ? 1f : (col == 4 ? 0.5f : 0f);
                float right = col >= 11 ? 1f : (col == 10 ? 0.5f : 0f);
                float m = Math.max(left, right) * rowW;
                if (0f >= m) continue;
                int i = row * 22 + col;
                float u = frac(t / FLAMEOUT_CYCLE + (left > 0f ? 0f : 0.5f));
                int base = kb[i];
                int c;
                if (0.4f > u) c = lerp(0x000000, FLAMEOUT_RED, smooth(u / 0.4f));          // dark -> red
                else if (0.5f > u) c = FLAMEOUT_RED;                                     // hold red
                else if (0.9f > u) c = lerp(FLAMEOUT_RED, base, smooth((u - 0.5f) / 0.4f)); // red -> normal
                else c = lerp(base, 0x000000, smooth((u - 0.9f) / 0.1f));                // normal -> dark
                kb[i] = lerp(base, c, m * flameLevel);
            }
        }
    }

    public static final int CR_YELLOW = 0xFFFF00, CR_RED = 0xFF1000;
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

    // keyboard numpad: rows 1..5, columns 18-19 = ammo bar, 20-21 = system bar
    static final int NUMPAD_TOP = 1, NUMPAD_ROWS = 5, NUMPAD_AMMO = 18, NUMPAD_SYS = 20;
    static final int MOUSE_WHEEL = 2 * 7 + 3, MOUSE_LOGO = 7 * 7 + 3;

    /**
     * gauges: mouse side strips (Razer mouse grid column 0 = left side, 6 = right side, rows 1..7
     * top to bottom) and the keyboard numpad split in two bars. Ammo on the left, the ship system
     * (charges / cooldown) on the right. The mouse wheel and logo show the system state like F.
     */
    private void gauges(int type, boolean on, float t) {
        int[] m = frame[Devices.MOUSE];
        int[] kb = frame[Devices.KEYBOARD];
        boolean hasAmmo = LightState.ammo >= 0f;
        for (int k = 0; 7 > k; k++) {
            if (hasAmmo) m[(7 - k) * 7] = ammoLed(k, 7, t);
            if (type != LightState.SYS_NONE) m[(7 - k) * 7 + 6] = systemLed(k, 7, type, on, t);
        }
        for (int k = 0; NUMPAD_ROWS > k; k++) {
            int row = (NUMPAD_TOP + NUMPAD_ROWS - 1 - k) * 22;
            int a = hasAmmo ? ammoLed(k, NUMPAD_ROWS, t) : 0x080808;
            int s = type != LightState.SYS_NONE ? systemLed(k, NUMPAD_ROWS, type, on, t) : 0x080808;
            kb[row + NUMPAD_AMMO] = a;
            kb[row + NUMPAD_AMMO + 1] = a;
            kb[row + NUMPAD_SYS] = s;
            kb[row + NUMPAD_SYS + 1] = s;
        }
        if (type != LightState.SYS_NONE) {
            int c = systemKey(type, on, t);
            m[MOUSE_WHEEL] = c;
            m[MOUSE_LOGO] = c;
        }
    }

    /** LED k (0 = bottom) of an n-LED ammo bar: brass, pulsing red below 25%, blinking red when empty */
    private int ammoLed(int k, int n, float t) {
        float ammo = LightState.ammo;
        if (0f >= ammo) return frac(t * 1.5f) > 0.5f ? Config.used : 0x200000;
        boolean low = 0.25f > ammo;
        int col = low ? Config.used : AMMO_BRASS;
        float pulse = low ? 0.6f + 0.4f * (float) Math.sin(t * 8f) : 1f;
        float fill = clamp01(ammo * n - k);
        return lerp(scale(col, 0.06f), scale(col, pulse), fill);
    }

    /** LED k (0 = bottom) of an n-LED system bar: charges in light blue, the reloading one / cooldown in red */
    private int systemLed(int k, int n, int type, boolean on, float t) {
        int max = LightState.systemMaxCharges;
        float rech = clamp01(LightState.systemRecharge);
        float pos = (k + 0.5f) / n;
        if (max > 0) {
            float have = LightState.systemCharges / (float) max;
            if (have >= pos) return on ? systemColor(type) : Config.ready;
            if ((LightState.systemCharges + rech) / max >= pos) return scale(Config.used, 0.75f);
            return 0x080808;
        }
        if (on) return scale(systemColor(type), 0.75f + 0.25f * (float) Math.sin(t * 18f + k));
        if (LightState.systemReady) return Config.ready;
        return rech >= pos ? scale(Config.used, 0.75f) : 0x080808;
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

    /** keyboard centre for the explosion, sensor burst and interdiction pulse: the middle of the letter keys (column 7.5 = G/H, row 3 = home row),
     *  not the middle of the whole 22-column grid, which sits over the right side of the main block */
    public static final float MID_KEYS_X = 7.5f / 21f;
    public static final float MID_KEYS_Y = 1f - 3f / 5f;

    /** flagship destroyed: white flash, fire ring from the middle, embers, then dark except Tab */
    private void renderDeath(float since, float t) {
        float life = since / EXPLOSION_SECONDS;
        float radius = since * 1.1f;
        for (int d = 0; d < Devices.COUNT; d++) {
            int[] a = frame[d];
            float[] xs = Devices.X[d];
            float[] ys = Devices.Y[d];
            float aspect = Devices.ASPECT[d];
            float cx = d == Devices.KEYBOARD ? MID_KEYS_X : 0.5f;
            float cy = d == Devices.KEYBOARD ? MID_KEYS_Y : 0.5f;
            // the ring has further to go from an off-centre start: scale by the farthest corner
            float reach = Math.max(1f, Math.max(cx, 1f - cx) * 2f);
            for (int i = 0; i < a.length; i++) {
                if (life >= 1f) { a[i] = 0; continue; }
                float dx = (xs[i] - cx) * aspect;
                float dy = ys[i] - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy) / Math.max(1f, aspect * 0.5f) / reach;
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
            // the command screen key (Tab) and X stay lit
            float pulse = 0.65f + 0.35f * (float) Math.sin(t * 2.5f);
            float in = Math.min(1f, (life - 0.85f) / 0.15f);
            int c = scale(Config.ready, life >= 1f ? pulse : pulse * in);
            frame[Devices.KEYBOARD][Devices.keyIndex(Devices.KEY_TAB)] = c;
            frame[Devices.KEYBOARD][Devices.keyIndex(Devices.KEY_X)] = c;
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

    public static int lerp(int a, int b, float t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int scale(int c, float f) {
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
