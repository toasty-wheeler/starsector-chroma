package starchroma;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

/** Options from data/config/settings.json (all optional). */
public class Config {

    /** campaign keyboard background: "status" (follows fleet movement + abilities), "spectrum", "wave" or "static" */
    public static String baseMode = "status";
    public static int baseColor = 0x00FF40;
    /** overall brightness multiplier 0..1 */
    public static float brightness = 1f;
    /** campaign background brightness, kept low so the highlighted keys stand out */
    public static float baseBrightness = 0.4f;
    /** false = in the campaign only the keyboard is driven; mouse, headset etc. keep your own Synapse lighting */
    public static boolean campaignAllDevices = false;
    /** campaign: also drive the mouse (supplies / fuel side bars, warning blinks on wheel and logo) */
    public static boolean campaignMouse = true;
    /** milliseconds between frames sent to Chroma (66 = 15 fps; Synapse can fall behind and then rush at 25+ fps) */
    public static int frameMs = 66;

    public static int ready = 0x55C8FF;    // light blue
    public static int used = 0xFF1408;     // red
    public static int combatWhite = 0xC8CDD7;
    public static int fluxHot = 0xE63C00;  // deep red-orange
    public static int shield = 0x5AB4FF;
    public static int phase = 0xA050FF;

    public static void load() {
        Logger log = Global.getLogger(Config.class);
        try { baseMode = Global.getSettings().getString("starchroma_baseMode"); } catch (Throwable t) { }
        try { baseColor = rgb(Global.getSettings().getColor("starchroma_baseColor"), baseColor); } catch (Throwable t) { }
        try { brightness = clamp(Global.getSettings().getFloat("starchroma_brightness")); } catch (Throwable t) { }
        try { baseBrightness = clamp(Global.getSettings().getFloat("starchroma_baseBrightness")); } catch (Throwable t) { }
        try { campaignAllDevices = Global.getSettings().getBoolean("starchroma_campaignAllDevices"); } catch (Throwable t) { }
        try { campaignMouse = Global.getSettings().getBoolean("starchroma_campaignMouse"); } catch (Throwable t) { }
        try { frameMs = Math.max(33, Math.min(200, Global.getSettings().getInt("starchroma_frameMs"))); } catch (Throwable t) { }
        try { ready = rgb(Global.getSettings().getColor("starchroma_readyColor"), ready); } catch (Throwable t) { }
        try { used = rgb(Global.getSettings().getColor("starchroma_usedColor"), used); } catch (Throwable t) { }
        if (baseMode == null) baseMode = "status";
        log.info("Star Chroma: base=" + baseMode + " brightness=" + brightness + " campaignAllDevices=" + campaignAllDevices + " frameMs=" + frameMs);
    }

    private static int rgb(Color c, int fallback) {
        if (c == null) return fallback;
        return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    private static float clamp(float v) {
        return v > 1f ? 1f : (0f > v ? 0f : v);
    }
}
