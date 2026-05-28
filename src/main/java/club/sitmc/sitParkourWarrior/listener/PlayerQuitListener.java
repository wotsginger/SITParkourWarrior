package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.session.SessionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final SessionManager sessionManager;

    public PlayerQuitListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessionManager.removeRunProgress(event.getPlayer().getUniqueId());
        sessionManager.endSession(event.getPlayer(), false);
    }
}
