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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class SITParkourWarrior extends JavaPlugin {

    private MapManager mapManager;
    private SessionManager sessionManager;
    private DynamicService dynamicService;
    private ParticleService particleService;
    private SelectionManager selectionManager;

    @Override
    public void onEnable() {
        // The server chooses to clear FAWE clipboard .bd files on startup; keep it explicit here.
        // Path resolved relative to server root: <server>/plugins/FastAsyncWorldEdit/clipboard
        cleanupFaweClipboardBdFiles();

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
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(sessionManager, mapManager), this);
        getServer().getPluginManager().registerEvents(new SelectionToolListener(selectionManager), this);

        DPCommand command = new DPCommand(mapManager, sessionManager, selectionManager, dynamicService);
        if (getCommand("sitpkw") != null) {
            getCommand("sitpkw").setExecutor(command);
            getCommand("sitpkw").setTabCompleter(command);
        } else {
            getLogger().warning("Command sitpkw not found in plugin.yml");
        }
    }

    private void cleanupFaweClipboardBdFiles() {
        File dataFolder = getDataFolder(); // <server>/plugins/SITParkourWarrior
        File pluginsDir = dataFolder == null ? null : dataFolder.getParentFile(); // <server>/plugins
        File serverRoot = pluginsDir == null ? null : pluginsDir.getParentFile(); // <server>
        if (serverRoot == null) {
            return;
        }
        Path clipboardDir = serverRoot.toPath()
                .resolve("plugins")
                .resolve("FastAsyncWorldEdit")
                .resolve("clipboard");
        if (!Files.isDirectory(clipboardDir)) {
            return;
        }

        AtomicInteger deleted = new AtomicInteger(0);
        try (var stream = Files.walk(clipboardDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".bd"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            deleted.incrementAndGet();
                        } catch (IOException e) {
                            getLogger().warning("Failed to delete FAWE clipboard file: " + p + " (" + e.getMessage() + ")");
                        }
                    });
        } catch (IOException e) {
            getLogger().warning("Failed to scan FAWE clipboard folder: " + clipboardDir + " (" + e.getMessage() + ")");
            return;
        }

        if (deleted.get() > 0) {
            getLogger().info("Deleted " + deleted.get() + " FAWE clipboard .bd file(s) under " + clipboardDir);
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
