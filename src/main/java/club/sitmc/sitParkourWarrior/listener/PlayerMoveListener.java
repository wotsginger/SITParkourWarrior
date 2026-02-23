package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.map.Deployment;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.ParkourMap;
import club.sitmc.sitParkourWarrior.map.Region;
import club.sitmc.sitParkourWarrior.map.SelectionManager;
import club.sitmc.sitParkourWarrior.session.ParkourSession;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {
    private final SessionManager sessionManager;
    private final MapManager mapManager;
    private final SelectionManager selectionManager;
    private final java.util.Map<java.util.UUID, String> insideDeployment = new java.util.HashMap<>();

    public PlayerMoveListener(SessionManager sessionManager, MapManager mapManager, SelectionManager selectionManager) {
        this.sessionManager = sessionManager;
        this.mapManager = mapManager;
        this.selectionManager = selectionManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (selectionManager.isEditing(player)) {
            return;
        }
        ParkourSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) {
            DeploymentMatch match = findDeploymentByRegion(to);
            if (match != null) {
                String current = insideDeployment.get(player.getUniqueId());
                if (current == null || !current.equals(match.deployment.getId())) {
                    sessionManager.startSession(player, match.map, match.deployment);
                }
                insideDeployment.put(player.getUniqueId(), match.deployment.getId());
            } else {
                insideDeployment.remove(player.getUniqueId());
            }
            return;
        }
        ParkourMap map = mapManager.getMap(session.getMapId());
        if (map == null) {
            return;
        }
        Deployment deployment = map.getDeployment(session.getDeploymentId());
        if (deployment == null) {
            sessionManager.endSession(player, false);
            return;
        }
        Region region = deployment.getRegion();
        boolean inside = region != null && region.contains(to);
        boolean fellBelowMinY = region != null && to.getBlockY() < region.getMinY();
        if (inside) {
            insideDeployment.put(player.getUniqueId(), deployment.getId());
        } else {
            insideDeployment.remove(player.getUniqueId());
        }
        if (!session.isInsideRegion() && inside) {
            if (session.isSuppressNextTitle()) {
                session.setSuppressNextTitle(false);
            } else {
                player.sendTitle(
                        map.getDifficulty().getTitleColor() + map.getTitle(),
                        "",
                        10, 40, 10
                );
            }
            if (!session.isStarted()) {
                session.startTimer(System.currentTimeMillis());
            }
            session.setInsideRegion(true);
        } else if (session.isInsideRegion() && !inside) {
            if (!session.isCompleted() && !fellBelowMinY) {
                session.stopTimer(System.currentTimeMillis());
            }
            session.setInsideRegion(false);
            session.setInsideStart(false);
        }

        if (deployment.getStart() != null && to.getWorld() != null
                && deployment.getStart().getWorld() != null
                && to.getWorld().getName().equals(deployment.getStart().getWorld().getName())) {
            int dx = Math.abs(to.getBlockX() - deployment.getStart().getBlockX());
            int dy = Math.abs(to.getBlockY() - deployment.getStart().getBlockY());
            int dz = Math.abs(to.getBlockZ() - deployment.getStart().getBlockZ());
            boolean inStart = dx <= 1 && dy <= 1 && dz <= 1;
            if (inStart && !session.isInsideStart()) {
                if (session.isSkipResetAtStartOnce()) {
                    session.setSkipResetAtStartOnce(false);
                    if (!session.isStarted()) {
                        session.startTimer(System.currentTimeMillis());
                    }
                } else {
                    session.resetTimer();
                    session.startTimer(System.currentTimeMillis());
                }
                session.setInsideStart(true);
            } else if (!inStart && session.isInsideStart()) {
                session.setInsideStart(false);
            }
        }

        if (session.isStarted() && deployment.getEnd() != null && to.getWorld() != null && deployment.getEnd().getWorld() != null
                && to.getWorld().getName().equals(deployment.getEnd().getWorld().getName())) {
            int dx = Math.abs(to.getBlockX() - deployment.getEnd().getBlockX());
            int dy = Math.abs(to.getBlockY() - deployment.getEnd().getBlockY());
            int dz = Math.abs(to.getBlockZ() - deployment.getEnd().getBlockZ());
            if (dx <= 1 && dy <= 1 && dz <= 1) {
                session.setCompleted(true);
                sessionManager.endSession(player, true);
                return;
            }
        }
        if (region != null) {
            if (to.getBlockY() < region.getMinY()) {
                sessionManager.teleportToStart(player);
            }
        }
    }

    private DeploymentMatch findDeploymentByRegion(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (ParkourMap map : mapManager.getMaps().values()) {
            for (Deployment deployment : map.getDeployments()) {
                Region region = deployment.getRegion();
                if (region != null && region.contains(location)) {
                    return new DeploymentMatch(map, deployment);
                }
            }
        }
        return null;
    }

    private static class DeploymentMatch {
        private final ParkourMap map;
        private final Deployment deployment;

        private DeploymentMatch(ParkourMap map, Deployment deployment) {
            this.map = map;
            this.deployment = deployment;
        }
    }
}
