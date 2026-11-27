package starchroma;

/** Measures how long Razer Chroma takes per keyboard frame at 25 fps (prints a histogram). */
public class Latency {
    public static void main(String[] args) throws Exception {
        ChromaClient c = new ChromaClient();
        if (!c.open()) {
            System.out.println("open failed: " + ChromaClient.lastError);
            return;
        }
        Thread.sleep(1200);
        int[] frame = new int[132];
        int n = 250;
        long[] took = new long[n];
        long worst = 0, total = 0, over40 = 0, over100 = 0;
        long next = System.currentTimeMillis();
        for (int k = 0; k < n; k++) {
            float b = 0.5f - 0.5f * (float) Math.cos(k * 0.04f * 1.4f);
            int v = Renderer.lerp(Renderer.scale(Config.ready, 0.12f), Renderer.scale(Config.ready, 0.6f), b);
            for (int i = 0; i < frame.length; i++) frame[i] = v;
            long t0 = System.nanoTime();
            c.send(Devices.KEYBOARD, frame);
            long ms = (System.nanoTime() - t0) / 1000000L;
            took[k] = ms;
            total += ms;
            if (ms > worst) worst = ms;
            if (ms > 40) over40++;
            if (ms > 100) over100++;
            if (k % 50 == 0) c.heartbeat();
            next += 40;
            long w = next - System.currentTimeMillis();
            if (w > 0) Thread.sleep(w);
        }
        c.close();
        System.out.println("frames " + n + "  avg " + (total / n) + " ms  worst " + worst + " ms  >40ms " + over40 + "  >100ms " + over100);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < n; k++) sb.append(took[k]).append(k % 25 == 24 ? "\n" : " ");
        System.out.println(sb);
    }
}
