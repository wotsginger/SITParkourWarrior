package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.session.ParkourSession;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerRespawnListener implements Listener {
    private final SessionManager sessionManager;

    public PlayerRespawnListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        ParkourSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        Location checkpoint = session.getCheckpoint();
        if (checkpoint != null && checkpoint.getWorld() != null) {
            session.setInsideRegion(true);
            session.setInsideStart(false);
            session.setSkipResetAtStartOnce(true);
            event.setRespawnLocation(checkpoint);
        }
    }
}
