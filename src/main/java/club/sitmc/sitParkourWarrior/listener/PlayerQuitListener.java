package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.visibility.VisibilityManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final SessionManager sessionManager;
    private final PkwWorldManager pkwWorldManager;
    private final VisibilityManager visibilityManager;

    public PlayerQuitListener(SessionManager sessionManager, PkwWorldManager pkwWorldManager,
                              VisibilityManager visibilityManager) {
        this.sessionManager = sessionManager;
        this.pkwWorldManager = pkwWorldManager;
        this.visibilityManager = visibilityManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (pkwWorldManager.isPkwWorld(player.getWorld())) {
            sessionManager.saveAndClearActiveRun(player);
        }
        visibilityManager.cleanupPlayer(player);
        // Belt-and-suspenders: ensure any non-PKW session state is also cleared
        sessionManager.removeRunProgress(event.getPlayer().getUniqueId());
        sessionManager.endSession(event.getPlayer(), false);
    }
}
