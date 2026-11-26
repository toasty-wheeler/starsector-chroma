package starchroma;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public class ChromaModPlugin extends BaseModPlugin {

    public void onApplicationLoad() throws Exception {
        Logger log = Global.getLogger(ChromaModPlugin.class);
        Config.load();
        LightState.mode = LightState.IDLE;
        ChromaEngine.start(log);
    }

    public void onGameLoad(boolean newGame) {
        try {
            // transient: never written into the save
            Global.getSector().removeTransientScriptsOfClass(CampaignWatcher.class);
            Global.getSector().addTransientScript(new CampaignWatcher());
        } catch (Throwable t) {
            Global.getLogger(ChromaModPlugin.class).warn("Star Chroma: could not add campaign watcher", t);
        }
    }
}
