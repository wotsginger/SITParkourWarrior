package club.sitmc.sitParkourWarrior;

import club.sitmc.sitParkourWarrior.command.DPCommand;
import club.sitmc.sitParkourWarrior.listener.PlayerDeathListener;
import club.sitmc.sitParkourWarrior.listener.PlayerMoveListener;
import club.sitmc.sitParkourWarrior.listener.PlayerQuitListener;
import club.sitmc.sitParkourWarrior.listener.PlayerRespawnListener;
import club.sitmc.sitParkourWarrior.listener.SelectionToolListener;
import club.sitmc.sitParkourWarrior.map.DynamicService;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.SelectionManager;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.util.ParticleService;
import org.bukkit.plugin.java.JavaPlugin;

public final class SITParkourWarrior extends JavaPlugin {

    private MapManager mapManager;
    private SessionManager sessionManager;
    private DynamicService dynamicService;
    private ParticleService particleService;
    private SelectionManager selectionManager;

    @Override
    public void onEnable() {
        this.mapManager = new MapManager(this);
        this.dynamicService = new DynamicService(this, mapManager);
        this.particleService = new ParticleService(this);
        this.selectionManager = new SelectionManager(mapManager, particleService);
        this.sessionManager = new SessionManager(this, mapManager, dynamicService);
        this.particleService.start(selectionManager);
        mapManager.loadAll();
        dynamicService.startAllDeployed();

        getServer().getPluginManager().registerEvents(new PlayerMoveListener(sessionManager, mapManager, selectionManager), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(sessionManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(sessionManager), this);
        getServer().getPluginManager().registerEvents(new SelectionToolListener(selectionManager), this);

        DPCommand command = new DPCommand(mapManager, sessionManager, selectionManager, dynamicService);
        if (getCommand("sitpkw") != null) {
            getCommand("sitpkw").setExecutor(command);
            getCommand("sitpkw").setTabCompleter(command);
        } else {
            getLogger().warning("Command sitpkw not found in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        if (dynamicService != null) {
            dynamicService.stopAll();
        }
        if (particleService != null) {
            particleService.stopAll();
        }
        if (mapManager != null) {
            mapManager.saveAll();
        }
    }
}
