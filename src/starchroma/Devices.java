package starchroma;

/**
 * Razer Chroma device grids (REST API layouts) and a normalised position for every LED:
 * x 0 = left .. 1 = right, y 0 = bottom .. 1 = top. Effects are written against these positions,
 * so "fill from bottom to top" or "ring from the middle" work the same on every device.
 */
public class Devices {

    public static final int KEYBOARD = 0;
    public static final int MOUSE = 1;
    public static final int MOUSEPAD = 2;
    public static final int HEADSET = 3;
    public static final int KEYPAD = 4;
    public static final int CHROMALINK = 5;
    public static final int COUNT = 6;

    public static final String[] NAMES = {"keyboard", "mouse", "mousepad", "headset", "keypad", "chromalink"};
    public static final String[] EFFECT = {"CHROMA_CUSTOM", "CHROMA_CUSTOM2", "CHROMA_CUSTOM", "CHROMA_CUSTOM", "CHROMA_CUSTOM", "CHROMA_CUSTOM"};
    /** rows == 0 means a flat 1-D array */
    public static final int[] ROWS = {6, 9, 0, 0, 4, 0};
    public static final int[] COLS = {22, 7, 15, 5, 5, 5};

    /** width/height ratio, so circles look round on wide devices */
    public static final float[] ASPECT = {3.2f, 0.8f, 1f, 4f, 1.25f, 4f};

    public static final float[][] X = new float[COUNT][];
    public static final float[][] Y = new float[COUNT][];

    // keyboard keys (Razer RZKEY codes: row << 8 | column)
    public static final int KEY_ESC = 0x0001;
    public static final int KEY_TAB = 0x0201;
    public static final int KEY_E = 0x0204;
    public static final int KEY_R = 0x0205;
    public static final int KEY_I = 0x0209;
    public static final int KEY_F = 0x0305;
    public static final int KEY_C = 0x0404;
    /** keys 1, 2, ... 9, 0 in ability-bar order */
    public static final int[] KEY_DIGITS = {0x0102, 0x0103, 0x0104, 0x0105, 0x0106, 0x0107, 0x0108, 0x0109, 0x010A, 0x010B};
    /** campaign screens: Tab map, E intel, C character, F fleet, R refit, I cargo */
    public static final int[] KEY_SCREENS = {KEY_TAB, KEY_E, KEY_C, KEY_F, KEY_R, KEY_I};

    static {
        for (int d = 0; d < COUNT; d++) {
            int n = size(d);
            X[d] = new float[n];
            Y[d] = new float[n];
            int rows = ROWS[d];
            int cols = COLS[d];
            for (int i = 0; i < n; i++) {
                if (rows > 0) {
                    int r = i / cols;
                    int c = i % cols;
                    X[d][i] = cols > 1 ? c / (float) (cols - 1) : 0.5f;
                    Y[d][i] = rows > 1 ? 1f - r / (float) (rows - 1) : 0.5f;
                } else {
                    X[d][i] = n > 1 ? i / (float) (n - 1) : 0.5f;
                    Y[d][i] = 0.5f;
                }
            }
        }
        // mousepad (Firefly-style 15 LEDs): right side top->bottom, bottom right->left, left side bottom->top
        for (int i = 0; i < 15; i++) {
            float x, y;
            if (i >= 10) { x = 0f; y = (i - 10) / 4f; }
            else if (i >= 5) { x = 1f - (i - 5) / 4f; y = 0f; }
            else { x = 1f; y = 1f - i / 4f; }
            X[MOUSEPAD][i] = x;
            Y[MOUSEPAD][i] = y;
        }
    }

    public static int size(int d) {
        return ROWS[d] > 0 ? ROWS[d] * COLS[d] : COLS[d];
    }

    /** index into the keyboard array for an RZKEY code */
    public static int keyIndex(int key) {
        return (key >> 8) * 22 + (key & 0xFF);
    }
}
