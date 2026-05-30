package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.records.RecordsManager;
import club.sitmc.sitParkourWarrior.session.RunProgress;
import club.sitmc.sitParkourWarrior.session.SessionManager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final SessionManager sessionManager;
    private final PkwWorldManager pkwWorldManager;
    private final RecordsManager recordsManager;

    public PlayerJoinListener(SessionManager sessionManager, PkwWorldManager pkwWorldManager,
                              RecordsManager recordsManager) {
        this.sessionManager = sessionManager;
        this.pkwWorldManager = pkwWorldManager;
        this.recordsManager = recordsManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        String worldName = player.getWorld().getName();
        if (!pkwWorldManager.isPkwWorld(worldName)) return;

        // Give interactive items
        WorldChangeListener.givePkwItems(player);

        // Restore saved run
        RecordsManager.SavedRunData saved = recordsManager.loadAndClearActiveRun(worldName, player.getUniqueId());
        if (saved == null) return;

        RunProgress rp = new RunProgress();
        rp.restoreFull(saved.elapsedMs, saved.medals,
                saved.stone, saved.bronze, saved.silver, saved.gold,
                saved.claimedLevels);
        sessionManager.restoreRunProgress(player.getUniqueId(), rp);
    }
}
