package starchroma;

import java.util.List;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;

/** Registered in data/config/settings.json "plugins"; publishes the flagship's state each frame. */
public class ChromaCombatPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;
    private ShipAPI tracked;
    private ShipAPI deadShip;
    private boolean failed;
    private boolean systemWasOn;

    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        tracked = null;
        deadShip = null;
        systemWasOn = false;
        LightState.resetCombat();
    }

    public void advance(float amount, List events) {
        try {
            update();
        } catch (Throwable t) {
            if (!failed) Global.getLogger(ChromaCombatPlugin.class).warn("Star Chroma: combat read failed", t);
            failed = true;
        }
    }

    private void update() {
        if (Global.getCurrentState() == GameState.TITLE) {
            // the title screen's background battle: give the user's own lighting back
            LightState.mode = LightState.IDLE;
            return;
        }
        if (engine == null) engine = Global.getCombatEngine();
        if (engine == null) return;
        LightState.mode = LightState.COMBAT;

        ShipAPI ship = engine.getPlayerShip();
        boolean alive = ship != null && ship.isAlive() && !ship.isHulk() && ship.getHitpoints() > 0f;
        LightState.combatAt = System.currentTimeMillis();

        if (alive && ship != deadShip && engine.isEntityInPlay(ship)) {
            tracked = ship;
            deadShip = null;
            LightState.dead = false;
        } else if (tracked != null && (!tracked.isAlive() || tracked.isHulk() || 0f >= tracked.getHitpoints())) {
            deadShip = tracked;
            tracked = null;
            LightState.deathAt = System.currentTimeMillis();
            LightState.dead = true;
        } else if (tracked != null && !engine.isEntityInPlay(tracked)) {
            // retreated / removed from the battle: its flux tracker freezes (e.g. stuck "overloaded")
            tracked = null;
        }

        if (tracked == null) {
            LightState.hasShip = false;
            LightState.systemOn = false;
            LightState.overloaded = false;
            LightState.venting = false;
            LightState.ammo = -1f;
            systemWasOn = false;
            return;
        }
        FluxTrackerAPI flux = tracked.getFluxTracker();
        ShieldAPI shield = tracked.getShield();
        LightState.flux = flux != null ? flux.getFluxLevel() : 0f;
        // isOverloaded() can lag behind; trust the remaining overload time too
        LightState.overloaded = flux != null && flux.isOverloaded() && flux.getOverloadTimeRemaining() > 0f;
        LightState.venting = flux != null && flux.isVenting();
        LightState.ammo = readAmmo(tracked);
        LightState.cr = tracked.getCurrentCR();
        LightState.hull = tracked.getHullLevel();
        LightState.shieldOn = shield != null && shield.isOn();
        LightState.phased = tracked.isPhased();
        readSystem(tracked);
        LightState.hasShip = true;
    }

    /** the F-key ship system (or the phase cloak on ships that have nothing else) */
    private void readSystem(ShipAPI ship) {
        ShipSystemAPI sys = ship.getSystem();
        if (sys == null) sys = ship.getPhaseCloak();
        if (sys == null) {
            LightState.systemType = LightState.SYS_NONE;
            LightState.systemOn = false;
            LightState.systemReady = false;
            LightState.systemLevel = 0f;
            systemWasOn = false;
            return;
        }
        boolean on = sys.isActive();
        long now = System.currentTimeMillis();
        if (on && !systemWasOn) LightState.systemAt = now;
        if (!on && systemWasOn) LightState.systemEndAt = now;
        systemWasOn = on;
        LightState.systemType = LightState.classifySystem(sys.getId());
        LightState.systemLevel = sys.getEffectLevel();
        LightState.systemOn = on;
        LightState.systemReady = !on && !sys.isCoolingDown() && !sys.isOutOfAmmo();

        int max = sys.getMaxAmmo();
        boolean charges = max >= 2 && 50 >= max;
        LightState.systemMaxCharges = charges ? max : 0;
        LightState.systemCharges = charges ? Math.max(0, Math.min(max, sys.getAmmo())) : 0;
        float recharge = 1f;
        if (charges && max > sys.getAmmo()) recharge = sys.getAmmoReloadProgress();
        else if (sys.isCoolingDown() && sys.getCooldown() > 0f) recharge = 1f - sys.getCooldownRemaining() / sys.getCooldown();
        else if (on) recharge = 0f;
        LightState.systemRecharge = Math.max(0f, Math.min(1f, recharge));
    }

    /** ammo left (0..1) in the selected weapon group, or across all ammo weapons if that group has none */
    private static float readAmmo(ShipAPI ship) {
        WeaponGroupAPI group = ship.getSelectedGroupAPI();
        float a = group != null ? ammoOf(group.getWeaponsCopy()) : -1f;
        if (0f > a) a = ammoOf(ship.getAllWeapons());
        return a;
    }

    private static float ammoOf(List weapons) {
        if (weapons == null) return -1f;
        float have = 0f, max = 0f;
        for (int i = 0; i < weapons.size(); i++) {
            WeaponAPI w = (WeaponAPI) weapons.get(i);
            if (w == null || w.isDecorative() || !w.usesAmmo()) continue;
            int m = w.getMaxAmmo();
            if (0 >= m || m > 100000) continue;
            have += Math.max(0, w.getAmmo());
            max += m;
        }
        return max > 0f ? have / max : -1f;
    }
}
