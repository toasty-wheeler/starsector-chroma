package starchroma;

import java.util.List;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.loading.AbilitySpecAPI;

/** Reads the fleet's abilities every frame in the campaign and publishes them for the lighting thread. */
public class CampaignWatcher implements EveryFrameScript {

    private boolean failed;
    private boolean sensorFired, pulseFired;
    private float lastSensor, lastPulse;
    private LocationAPI lastLocation;
    private long jumpSeenAt;

    public boolean isDone() { return false; }

    public boolean runWhilePaused() { return true; }

    public void advance(float amount) {
        try {
            if (Global.getCurrentState() != GameState.CAMPAIGN) return;
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            LightState.slots = readSlots(fleet);
            if (fleet != null) readFleet(fleet);
            LightState.mode = LightState.CAMPAIGN;
        } catch (Throwable t) {
            if (!failed) Global.getLogger(CampaignWatcher.class).warn("Star Chroma: campaign read failed", t);
            failed = true;
        }
    }

    private void readFleet(CampaignFleetAPI fleet) {
        long now = System.currentTimeMillis();

        // movement status, strongest first
        int move = LightState.MOVE_NORMAL;
        if (busy(fleet.getAbility("emergency_burn"))) move = LightState.MOVE_EMERGENCY;
        else if (on(fleet.getAbility("sustained_burn"))) move = LightState.MOVE_SUSTAINED;
        else if (on(fleet.getAbility("go_dark")) || fleet.wasSlowMoving()) move = LightState.MOVE_SILENT;
        LightState.move = move;

        LightState.neutrino = on(fleet.getAbility("gravitic_scan"));

        // active sensor burst: charge, then a ring when it goes off. The "gone off" window can be
        // shorter than a frame (interdiction pulse lasts 0.001 days), so a charge that was nearly
        // full and then vanished also counts as fired.
        float c = charge(fleet.getAbility("sensor_burst"));
        if (c >= 1f || (0f >= c && lastSensor > 0.7f)) {
            if (!sensorFired) LightState.sensorAt = now;
            sensorFired = true;
        } else if (0f >= c) {
            sensorFired = false;
        }
        lastSensor = c >= 1f ? 0f : c;
        LightState.sensorCharge = c >= 1f ? 0f : c;

        // interdiction pulse: same shape
        c = charge(fleet.getAbility("interdiction_pulse"));
        if (c >= 1f || (0f >= c && lastPulse > 0.7f)) {
            if (!pulseFired) LightState.pulseAt = now;
            pulseFired = true;
        } else if (0f >= c) {
            pulseFired = false;
        }
        lastPulse = c >= 1f ? 0f : c;
        LightState.pulseCharge = c >= 1f ? 0f : c;

        // transverse jump: charge while winding up; the jump itself shows up as a location change
        c = charge(fleet.getAbility("fracture_jump"));
        if (c > 0f) jumpSeenAt = now;
        LightState.jumpCharge = c >= 1f ? 1f : Math.max(0f, c);
        LocationAPI loc = fleet.getContainingLocation();
        if (loc != null && lastLocation != null && loc != lastLocation && 6000L > now - jumpSeenAt) {
            LightState.jumpDoneAt = now;
            jumpSeenAt = 0L;
        }
        if (loc != null) lastLocation = loc;
    }

    private static boolean on(AbilityPlugin ab) {
        return ab != null && ab.isActive();
    }

    private static boolean busy(AbilityPlugin ab) {
        return ab != null && ab.isActiveOrInProgress();
    }

    /**
     * For a duration ability: 0 when idle, 0..1 while charging up, 1 or more once it has gone off
     * (until the whole duration is over).
     */
    private static float charge(AbilityPlugin ab) {
        if (ab == null || !ab.isInProgress()) return 0f;
        float p = ab.getProgressFraction();
        if (0f >= p) return 0.001f;
        AbilitySpecAPI spec = ab.getSpec();
        float a = spec.getActivationDays();
        float total = a + spec.getDurationDays() + spec.getDeactivationDays();
        if (0f >= a || 0f >= total) return 1f;
        float frac = a / total;
        if (p >= frac) return 1f;
        return Math.max(0.001f, p / frac);
    }

    private static int[] readSlots(CampaignFleetAPI fleet) {
        int[] out = new int[10];
        if (fleet == null) return out;
        AbilitySlotsAPI bar = Global.getSector().getUIData().getAbilitySlotsAPI();
        if (bar == null) return out;
        boolean hyper = fleet.isInHyperspace();
        List slots = bar.getCurrSlotsCopy();
        for (int i = 0; i < slots.size(); i++) {
            AbilitySlotAPI slot = (AbilitySlotAPI) slots.get(i);
            int key = slot.getSlotId();
            if (0 > key || key > 9) continue;
            String id = slot.getAbilityId();
            if (hyper && slot.getInHyperAbilityId() != null) id = slot.getInHyperAbilityId();
            if (id == null) continue;
            AbilityPlugin ab = fleet.getAbility(id);
            if (ab == null) continue;
            boolean busy = ab.isActiveOrInProgress() || ab.isOnCooldown() || !ab.isUsable();
            out[key] = busy ? LightState.SLOT_USED : LightState.SLOT_READY;
        }
        return out;
    }
}
