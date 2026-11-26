package starchroma;

import org.apache.log4j.Logger;

/**
 * Background thread that owns the Chroma session and pushes frames ~25 times a second.
 * The game thread never waits on it; it only writes LightState.
 */
public class ChromaEngine extends Thread {

    private static ChromaEngine instance;

    private final Logger log;
    private final ChromaClient client = new ChromaClient();
    private final Renderer renderer = new Renderer();
    private final int[][] sent = new int[Devices.COUNT][];
    private final long[] sentAt = new long[Devices.COUNT];
    private volatile boolean running = true;

    private int sessionMode = -1;   // mode the open session was made for
    private long sessionReadyAt;    // Chroma ignores effects for a moment after init
    private long lastBeat;
    private long retryAt;
    private int failures;
    private boolean wasDead;

    private ChromaEngine(Logger log) {
        super("StarChroma lighting");
        this.log = log;
        setDaemon(true);

    }

    public static synchronized void start(Logger log) {
        if (instance != null) return;
        instance = new ChromaEngine(log);
        instance.start();
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
    }

    public static synchronized void shutdown() {
        if (instance == null) return;
        instance.running = false;
        instance.interrupt();
        try { instance.join(1500); } catch (InterruptedException e) { }
        instance = null;
    }

    public void run() {
        while (running) {
            long now = System.currentTimeMillis();
            try {
                step(now);
            } catch (Throwable t) {
                log.warn("Star Chroma: lighting step failed", t);
            }
            try { Thread.sleep(40); } catch (InterruptedException e) { }
        }
        client.close();
    }

    private void step(long now) {
        int mode = LightState.mode;

        if (mode == LightState.IDLE) {
            if (client.isOpen()) {
                client.close();
                sessionMode = -1;
            }
            return;
        }

        // leaving combat for the campaign: start a fresh session so devices we no longer drive
        // (mouse, headset, ...) go back to the user's own Synapse lighting
        if (client.isOpen() && sessionMode == LightState.COMBAT && mode == LightState.CAMPAIGN && !Config.campaignAllDevices) {
            client.close();
        }

        if (!client.isOpen()) {
            if (retryAt > now) return;
            if (!client.open()) {
                retryAt = now + 5000;
                if (5 > failures) {
                    failures++;
                    log.info("Star Chroma: could not register with Razer Chroma (" + ChromaClient.lastError + "), retrying in 5 s");
                    if (failures == 5) log.info("Star Chroma: still retrying, not logging further failures");
                }
                return;
            }
            failures = 0;
            log.info("Star Chroma: Chroma session opened");
            sessionReadyAt = now + 1200;
            lastBeat = now;
            for (int d = 0; d < Devices.COUNT; d++) sent[d] = null;
        }
        if (mode != sessionMode) {
            if (mode == LightState.COMBAT) renderer.resetCombat();
            sessionMode = mode;
        }

        if (now - lastBeat > 2000) {
            lastBeat = now;
            if (!client.heartbeat()) {
                lostConnection(now);
                return;
            }
        }
        if (sessionReadyAt > now) return;

        boolean dead = LightState.dead;
        if (dead != wasDead && !dead) renderer.resetCombat();
        wasDead = dead;

        int[][] frame = renderer.render(now);
        boolean all = mode == LightState.COMBAT || Config.campaignAllDevices;
        for (int d = 0; d < Devices.COUNT; d++) {
            if (!all && d != Devices.KEYBOARD) continue;
            // only resend unchanged frames once a second
            if (sent[d] != null && same(sent[d], frame[d]) && 1000 > now - sentAt[d]) continue;
            if (!client.send(d, frame[d])) {
                lostConnection(now);
                return;
            }
            if (sent[d] == null) sent[d] = new int[frame[d].length];
            System.arraycopy(frame[d], 0, sent[d], 0, frame[d].length);
            sentAt[d] = now;
        }
    }

    private void lostConnection(long now) {
        log.info("Star Chroma: lost the Chroma session (" + ChromaClient.lastError + "), reconnecting");
        client.close();
        sessionMode = -1;
        retryAt = now + 3000;
    }

    private static boolean same(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    /** releases the session on exit so Synapse lighting comes straight back */
    static class ShutdownHook extends Thread {
        public void run() {
            LightState.mode = LightState.IDLE;
            shutdown();
        }
    }
}
