package starchroma;

import java.util.List;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;

/** Reads the ability bar every frame in the campaign and publishes it for the lighting thread. */
public class CampaignWatcher implements EveryFrameScript {

    private boolean failed;

    public boolean isDone() { return false; }

    public boolean runWhilePaused() { return true; }

    public void advance(float amount) {
        try {
            if (Global.getCurrentState() != GameState.CAMPAIGN) return;
            LightState.mode = LightState.CAMPAIGN;
            LightState.slots = readSlots();
        } catch (Throwable t) {
            if (!failed) Global.getLogger(CampaignWatcher.class).warn("Star Chroma: campaign read failed", t);
            failed = true;
        }
    }

    private static int[] readSlots() {
        int[] out = new int[10];
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
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
