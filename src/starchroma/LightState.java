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

    /** fleet movement status, drives the campaign background */
    public static final int MOVE_NORMAL = 0;
    public static final int MOVE_SILENT = 1;     // Go Dark, or moving slowly
    public static final int MOVE_SUSTAINED = 2;
    public static final int MOVE_EMERGENCY = 3;

    // campaign
    public static volatile int[] slots = new int[10];
    public static volatile int move = MOVE_NORMAL;
    public static volatile boolean neutrino;
    /** charge-up of a duration ability, 0 = not charging, 0..1 while it winds up */
    public static volatile float sensorCharge;
    public static volatile float pulseCharge;
    public static volatile float jumpCharge;
    /** System.currentTimeMillis() when the ability went off (0 = never) */
    public static volatile long sensorAt;
    public static volatile long pulseAt;
    /** when a transverse jump actually moved the fleet */
    public static volatile long jumpDoneAt;

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

    /** what kind of ship system (F key) the flagship has */
    public static final int SYS_NONE = 0;
    public static final int SYS_CHARGE = 1;    // burn drive, plasma burn, maneuvering jets...
    public static final int SYS_TELEPORT = 2;  // phase teleporter, phase skimmer, displacer
    public static final int SYS_PHASE = 3;     // phase cloak used as the ship system
    public static final int SYS_FEEDER = 4;    // accelerated ammo feeder, fast missile racks, high energy focus
    public static final int SYS_OTHER = 5;
    public static volatile int systemType;
    /** 0..1 how strongly the system is working right now (its effect level) */
    public static volatile float systemLevel;
    /** charging up, on, or charging down */
    public static volatile boolean systemOn;
    /** can be activated right now (not on, not cooling down, has charges) */
    public static volatile boolean systemReady;
    /** System.currentTimeMillis() when the system was switched on / finished (0 = never) */
    public static volatile long systemAt;
    public static volatile long systemEndAt;
    /** system charges (e.g. Phase Skimmer); max 0 = the system has no charges, only a cooldown */
    public static volatile int systemCharges;
    public static volatile int systemMaxCharges;
    /** 0..1 progress of the next charge reload, or of the cooldown (1 = ready) */
    public static volatile float systemRecharge = 1f;
    /** 0..1 ammo left in the selected weapon group (or the whole ship); -1 = no ammo weapons */
    public static volatile float ammo = -1f;
    /** System.currentTimeMillis() of the last combat update; stale state is not shown */
    public static volatile long combatAt;

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
        systemType = SYS_NONE;
        systemLevel = 0f;
        systemOn = false;
        systemReady = false;
        systemAt = 0L;
        systemEndAt = 0L;
        systemCharges = 0;
        systemMaxCharges = 0;
        systemRecharge = 1f;
        ammo = -1f;
        combatAt = 0L;
    }

    /** sorts a ship system id into a lighting style; keyword fallbacks cover modded systems */
    public static int classifySystem(String id) {
        if (id == null) return SYS_NONE;
        String s = id.toLowerCase();
        if (has(s, "teleport") || has(s, "displacer") || has(s, "skimmer") || has(s, "blink")) return SYS_TELEPORT;
        if (has(s, "phase") || has(s, "cloak")) return SYS_PHASE;
        if (has(s, "feed") || has(s, "racks") || has(s, "focus") || has(s, "autoload")
            || has(s, "reload") || has(s, "ammo")) return SYS_FEEDER;
        if (has(s, "burn") || has(s, "jets") || has(s, "lunge") || has(s, "drive") || has(s, "dash")
            || has(s, "charge") || has(s, "rush") || has(s, "boost")) return SYS_CHARGE;
        return SYS_OTHER;
    }

    private static boolean has(String s, String part) {
        return s.indexOf(part) >= 0;
    }
}
