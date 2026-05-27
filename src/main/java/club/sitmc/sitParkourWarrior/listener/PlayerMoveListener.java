package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.map.CourseLinker;
import club.sitmc.sitParkourWarrior.map.Deployment;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.NodeType;
import club.sitmc.sitParkourWarrior.map.ParkourMap;
import club.sitmc.sitParkourWarrior.map.Region;
import club.sitmc.sitParkourWarrior.map.SelectionManager;
import club.sitmc.sitParkourWarrior.session.ParkourSession;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.session.SessionState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.List;

public class PlayerMoveListener implements Listener {
    private final SessionManager sessionManager;
    private final MapManager mapManager;
    private final SelectionManager selectionManager;
    private final java.util.Map<java.util.UUID, String> insideDeployment = new java.util.HashMap<>();

    public PlayerMoveListener(SessionManager sessionManager, MapManager mapManager,
                              SelectionManager selectionManager) {
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

        // Death-line check must execute before the same-block return below.
        // A purely vertical fall can produce consecutive move events whose
        // block Y differs but X/Z block coords are unchanged, making the
        // same-block check *not* match — however, slow diagonal movement or
        // sub-block Y changes within the same integer block can still be
        // skipped.  Placing the check first guarantees every move event with
        // a valid session is tested against the current death-line.
        Player player = event.getPlayer();
        ParkourSession session = sessionManager.getSession(player.getUniqueId());
        if (session != null && to.getBlockY() < session.getDeathLineY()) {
            ParkourMap deathMap = mapManager.getMap(session.getMapId());
            if (deathMap != null && deathMap.getNodeType() == NodeType.FORK) {
                sessionManager.handleForkFallback(player, session);
            } else {
                sessionManager.teleportToCheckpoint(player);
            }
            return;
        }

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (selectionManager.isEditing(player)) {
            return;
        }

        // ---- No session: auto-start when entering a deployment region ----
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
            sessionManager.endSession(player, false);
            return;
        }
        Deployment deployment = map.getDeployment(session.getDeploymentId());
        if (deployment == null) {
            sessionManager.endSession(player, false);
            return;
        }

        // ---- Handoff check for non-FORK nodes (RUNNING or AWAITING_HANDOFF) ----
        if (map.getNodeType() != NodeType.FORK
                && (session.getState() == SessionState.RUNNING || session.getState() == SessionState.AWAITING_HANDOFF)) {
            if (checkHandoff(player, session, to)) {
                return;
            }
            if (session.getState() == SessionState.AWAITING_HANDOFF) {
                return; // No matching entry point nearby, keep waiting.
            }
            // RUNNING with no match: fall through to normal RUNNING logic below.
        }

        // ---- FORK runtime: track visited points, check handoff out ----
        if (map.getNodeType() == NodeType.FORK) {
            handleForkRuntime(player, session, map, deployment, to);
            return;
        }

        // ---- Active (RUNNING) session: timer, start, end logic ----
        Region region = deployment.getRegion();
        boolean wasInside = session.isInsideRegion();
        boolean inside = region != null && region.contains(to);

        if (inside) {
            insideDeployment.put(player.getUniqueId(), deployment.getId());
        } else {
            insideDeployment.remove(player.getUniqueId());
        }

        if (!wasInside && inside) {
            session.setInsideStart(false);
            session.setPendingTitleAtStart(true);
            if (!session.isStarted() && map.getNodeType() == NodeType.LEVEL) {
                session.startTimer(System.currentTimeMillis());
            }
            session.setInsideRegion(true);
        } else if (wasInside && !inside) {
            if (!session.isCompleted() && session.isStarted()) {
                session.stopTimer(System.currentTimeMillis());
            }
            session.setInsideRegion(false);
            session.setInsideStart(false);
        }

        Location start = sessionManager.resolveLocationWorld(deployment.getStart(), region);
        if (start != null && to.getWorld() != null
                && start.getWorld() != null
                && to.getWorld().getName().equals(start.getWorld().getName())) {
            int dx = Math.abs(to.getBlockX() - start.getBlockX());
            int dy = Math.abs(to.getBlockY() - start.getBlockY());
            int dz = Math.abs(to.getBlockZ() - start.getBlockZ());
            boolean inStart = dx <= 1 && dy <= 1 && dz <= 1;
            if (inStart && !session.isInsideStart()) {
                if (session.isPendingTitleAtStart()) {
                    player.sendTitle(
                            map.getDifficulty().getTitleColor() + map.getTitle(),
                            "",
                            10, 40, 10
                    );
                    session.setPendingTitleAtStart(false);
                }
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

        Location end = sessionManager.resolveLocationWorld(deployment.getEnd(), region);
        if (end != null && to.getWorld() != null && end.getWorld() != null
                && to.getWorld().getName().equals(end.getWorld().getName())) {
            int dx = Math.abs(to.getBlockX() - end.getBlockX());
            int dy = Math.abs(to.getBlockY() - end.getBlockY());
            int dz = Math.abs(to.getBlockZ() - end.getBlockZ());
            if (dx <= 1 && dy <= 1 && dz <= 1) {
                sessionManager.endSession(player, true);
                return;
            }
        }
    }

    /**
     * Check whether the player has reached any deployed node's entry point
     * (3x3x3 range). First match wins when multiple entry points overlap
     * (deterministic: map iteration order).
     * Entry points: LEVEL→start, FORK→each forkPoint, GLOBAL_END/BRANCH_END→end.
     * GLOBAL_START has no entry point and is skipped.
     * Own deployment is always excluded.
     *
     * @return true if a handoff was executed
     */
    private boolean checkHandoff(Player player, ParkourSession session, Location to) {
        for (ParkourMap map : mapManager.getMaps().values()) {
            if (map.getNodeType() == NodeType.GLOBAL_START) {
                continue;
            }
            for (Deployment dep : map.getDeployments()) {
                if (map.getId().equalsIgnoreCase(session.getMapId())
                        && dep.getId().equals(session.getDeploymentId())) {
                    continue;
                }
                List<Location> entryPoints = getEntryPoints(map, dep);
                for (Location ep : entryPoints) {
                    if (isNear(to, ep)) {
                        if (session.getState() == SessionState.RUNNING) {
                            insideDeployment.remove(player.getUniqueId());
                        }
                        sessionManager.handoffToNextDeployment(player, session, map, dep, ep);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Return the entry-point locations for a deployment.
     * LEVEL → start; FORK → each resolved fork point;
     * GLOBAL_END/BRANCH_END → end; GLOBAL_START → empty.
     */
    private List<Location> getEntryPoints(ParkourMap map, Deployment dep) {
        List<Location> points = new ArrayList<>();
        NodeType type = map.getNodeType();
        if (type == NodeType.GLOBAL_START) {
            return points;
        }
        Region region = dep.getRegion();
        if (type == NodeType.FORK) {
            points.addAll(CourseLinker.resolveForkBranchPoints(map, dep));
        } else if (type == NodeType.GLOBAL_END || type == NodeType.BRANCH_END) {
            Location end = sessionManager.resolveLocationWorld(dep.getEnd(), region);
            if (end != null) {
                points.add(end);
            }
        } else {
            Location start = sessionManager.resolveLocationWorld(dep.getStart(), region);
            if (start != null) {
                points.add(start);
            }
        }
        return points;
    }

    /**
     * FORK runtime: track visited fork points and check handoff out to next LEVEL.
     */
    private void handleForkRuntime(Player player, ParkourSession session, ParkourMap map, Deployment deployment, Location to) {
        Region region = deployment.getRegion();

        // Track visited fork points.
        List<Location> forkPoints = CourseLinker.resolveForkBranchPoints(map, deployment);
        for (Location fp : forkPoints) {
            if (isNear(to, fp) && !containsLocation(session.getVisitedForkPoints(), fp)) {
                session.getVisitedForkPoints().add(fp.clone());
            }
        }

        // Check handoff out: any other node's entry point nearby.
        if (checkHandoff(player, session, to)) {
            return;
        }

        // Region inside/outside tracking.
        boolean inside = region != null && region.contains(to);
        if (!session.isInsideRegion() && inside) {
            session.setInsideRegion(true);
        } else if (session.isInsideRegion() && !inside) {
            session.setInsideRegion(false);
        }
    }

    private boolean isNear(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        if (!a.getWorld().getName().equals(b.getWorld().getName())) {
            return false;
        }
        int dx = Math.abs(a.getBlockX() - b.getBlockX());
        int dy = Math.abs(a.getBlockY() - b.getBlockY());
        int dz = Math.abs(a.getBlockZ() - b.getBlockZ());
        return dx <= 1 && dy <= 1 && dz <= 1;
    }

    private boolean containsLocation(List<Location> list, Location loc) {
        if (loc.getWorld() == null) {
            return false;
        }
        for (Location l : list) {
            if (l.getWorld() != null
                    && l.getWorld().getName().equals(loc.getWorld().getName())
                    && l.getBlockX() == loc.getBlockX()
                    && l.getBlockY() == loc.getBlockY()
                    && l.getBlockZ() == loc.getBlockZ()) {
                return true;
            }
        }
        return false;
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
