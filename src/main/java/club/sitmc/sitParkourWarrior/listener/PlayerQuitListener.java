package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.records.RecordsManager;
import club.sitmc.sitParkourWarrior.session.RunProgress;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.visibility.VisibilityManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final SessionManager sessionManager;
    private final PkwWorldManager pkwWorldManager;
    private final RecordsManager recordsManager;
    private final VisibilityManager visibilityManager;

    public PlayerQuitListener(SessionManager sessionManager, PkwWorldManager pkwWorldManager,
                              RecordsManager recordsManager, VisibilityManager visibilityManager) {
        this.sessionManager = sessionManager;
        this.pkwWorldManager = pkwWorldManager;
        this.recordsManager = recordsManager;
        this.visibilityManager = visibilityManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (pkwWorldManager.isPkwWorld(player.getWorld())) {
            RunProgress rp = sessionManager.getRunProgress(player.getUniqueId());
            if (rp != null) {
                rp.pause();
                recordsManager.saveActiveRunFull(player.getWorld().getName(),
                        player.getUniqueId(), player.getName(),
                        rp.getElapsedMs(), rp.getMedals(),
                        rp.getStoneCount(), rp.getBronzeCount(), rp.getSilverCount(), rp.getGoldCount(),
                        rp.getClaimedLevelsWithTypes());
            }
        }
        visibilityManager.cleanupPlayer(player);
        sessionManager.removeRunProgress(event.getPlayer().getUniqueId());
        sessionManager.endSession(event.getPlayer(), false);
    }
}
