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

        if (alive && ship != deadShip) {
            tracked = ship;
            deadShip = null;
            LightState.dead = false;
        } else if (tracked != null && (!tracked.isAlive() || tracked.isHulk() || 0f >= tracked.getHitpoints())) {
            deadShip = tracked;
            tracked = null;
            LightState.deathAt = System.currentTimeMillis();
            LightState.dead = true;
        }

        if (tracked == null) {
            LightState.hasShip = false;
            LightState.systemOn = false;
            systemWasOn = false;
            return;
        }
        FluxTrackerAPI flux = tracked.getFluxTracker();
        ShieldAPI shield = tracked.getShield();
        LightState.flux = flux != null ? flux.getFluxLevel() : 0f;
        LightState.overloaded = flux != null && flux.isOverloaded();
        LightState.venting = flux != null && flux.isVenting();
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
    }
}
