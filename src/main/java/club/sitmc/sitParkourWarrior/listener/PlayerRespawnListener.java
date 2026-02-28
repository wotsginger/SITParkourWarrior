package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.map.Deployment;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.ParkourMap;
import club.sitmc.sitParkourWarrior.map.Region;
import club.sitmc.sitParkourWarrior.session.ParkourSession;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerRespawnListener implements Listener {
    private final SessionManager sessionManager;
    private final MapManager mapManager;

    public PlayerRespawnListener(SessionManager sessionManager, MapManager mapManager) {
        this.sessionManager = sessionManager;
        this.mapManager = mapManager;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        ParkourSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        ParkourMap map = mapManager.getMap(session.getMapId());
        if (map == null) {
            return;
        }
        Deployment deployment = map.getDeployment(session.getDeploymentId());
        if (deployment == null) {
            return;
        }
        Region region = deployment.getRegion();
        Location start = sessionManager.resolveLocationWorld(deployment.getStart(), region);
        if (start != null) {
            session.setInsideRegion(true);
            session.setInsideStart(false);
            session.setSkipResetAtStartOnce(true);
            event.setRespawnLocation(start);
        }
    }
}
