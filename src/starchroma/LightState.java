package starchroma;

/**
 * What the game is doing right now, written by the game thread and read by the lighting thread.
 * Only plain volatile fields; arrays are replaced whole, never edited in place.
 */
public class LightState {

    public static final int IDLE = 0;      // title screen / nothing to show: Chroma goes back to the user's own lighting
    public static final int CAMPAIGN = 1;
    public static final int COMBAT = 2;

    /** ability slot states for keys 1..0 */
    public static final int SLOT_EMPTY = 0;
    public static final int SLOT_READY = 1;
    public static final int SLOT_USED = 2;

    public static volatile int mode = IDLE;

    // campaign
    public static volatile int[] slots = new int[10];

    // combat
    public static volatile boolean hasShip;
    public static volatile float flux;        // 0..1
    public static volatile float hull = 1f;   // 0..1
    public static volatile boolean shieldOn;
    public static volatile boolean phased;
    public static volatile boolean overloaded;
    public static volatile boolean venting;
    public static volatile boolean dead;
    /** System.currentTimeMillis() when the flagship blew up */
    public static volatile long deathAt;

    public static void resetCombat() {
        hasShip = false;
        flux = 0f;
        hull = 1f;
        shieldOn = false;
        phased = false;
        overloaded = false;
        venting = false;
        dead = false;
        deathAt = 0L;
    }
}
