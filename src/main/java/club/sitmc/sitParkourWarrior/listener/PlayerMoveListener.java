package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.course.CourseLayoutAnalyzer;
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
import club.sitmc.sitParkourWarrior.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class PlayerMoveListener implements Listener {
    private final SessionManager sessionManager;
    private final MapManager mapManager;
    private final SelectionManager selectionManager;
    private final PkwWorldManager pkwWorldManager;
    private final CourseLayoutAnalyzer courseLayoutAnalyzer;
    private final java.util.Map<java.util.UUID, String> insideDeployment = new java.util.HashMap<>();

    public PlayerMoveListener(SessionManager sessionManager, MapManager mapManager,
                              SelectionManager selectionManager, PkwWorldManager pkwWorldManager,
                              CourseLayoutAnalyzer courseLayoutAnalyzer) {
        this.sessionManager = sessionManager;
        this.mapManager = mapManager;
        this.selectionManager = selectionManager;
        this.pkwWorldManager = pkwWorldManager;
        this.courseLayoutAnalyzer = courseLayoutAnalyzer;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        boolean isPkw = pkwWorldManager.isPkwWorld(to.getWorld());
        Player player = event.getPlayer();

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        if (selectionManager.isEditing(player)) {
            return;
        }

        if (isPkw) {
            handlePkwMove(player, from, to);
        } else {
            handleSimpleMove(player, from, to);
        }
    }

    // ============ Segment special-mode check ============

    private static boolean isInSegmentSpecialMode(Player player) {
        var key = new NamespacedKey("sitsegment", "special_mode");
        Byte v = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    // ============ PKW-world full gameplay ============

    private void handlePkwMove(Player player, Location from, Location to) {
        // Segment special mode: quit active run and stay silent
        if (isInSegmentSpecialMode(player)) {
            ParkourSession s = sessionManager.getSession(player.getUniqueId());
            if (s != null || sessionManager.getRunProgress(player.getUniqueId()) != null) {
                sessionManager.quitRun(player);
                sessionManager.endSession(player, false);
            }
            return;
        }

        ParkourSession session = sessionManager.getSession(player.getUniqueId());

        // Death-line check
        if (session != null && to.getBlockY() < session.getDeathLineY()) {
            ParkourMap deathMap = mapManager.getMap(session.getMapId());
            if (deathMap != null && deathMap.getNodeType() == NodeType.FORK) {
                sessionManager.handleForkFallback(player, session);
            } else {
                sessionManager.teleportToCheckpoint(player);
            }
            return;
        }

        // Lava death
        if (session != null && isLava(to)) {
            ParkourMap deathMap = mapManager.getMap(session.getMapId());
            if (deathMap != null && deathMap.getNodeType() == NodeType.FORK) {
                sessionManager.handleForkFallback(player, session);
            } else {
                sessionManager.teleportToCheckpoint(player);
            }
            return;
        }

        // Full-course timing
        checkFullCourseTiming(player, to);

        // Countdown timeout
        checkCountdownTimeout(player, session);

        // BRANCH_END teleport
        if (handleBranchEndTeleport(player, to)) return;

        // No session: auto-start
        if (session == null) {
            DeploymentMatch match = findDeploymentByEntryPoint(to);
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
        if (map == null) { sessionManager.endSession(player, false); return; }
        Deployment deployment = map.getDeployment(session.getDeploymentId());
        if (deployment == null) { sessionManager.endSession(player, false); return; }

        // Handoff check
        if (map.getNodeType() != NodeType.FORK
                && (session.getState() == SessionState.RUNNING || session.getState() == SessionState.AWAITING_HANDOFF)) {
            if (checkHandoff(player, session, to)) return;
            if (session.getState() == SessionState.AWAITING_HANDOFF) return;
        }

        // FORK runtime
        if (map.getNodeType() == NodeType.FORK) {
            handleForkRuntime(player, session, map, deployment, to);
            return;
        }

        // LEVEL runtime
        Region region = deployment.getRegion();
        boolean wasInside = session.isInsideRegion();
        boolean inside = region != null && region.contains(to);
        if (inside) insideDeployment.put(player.getUniqueId(), deployment.getId());
        else insideDeployment.remove(player.getUniqueId());

        if (!wasInside && inside) {
            session.setInsideStart(false);
            session.setPendingTitleAtStart(true);
            if (!session.isStarted() && map.getNodeType() == NodeType.LEVEL) {
                session.startTimer(System.currentTimeMillis());
            }
            session.setInsideRegion(true);
        } else if (wasInside && !inside) {
            if (!session.isCompleted() && session.isStarted()) session.stopTimer(System.currentTimeMillis());
            session.setInsideRegion(false);
            session.setInsideStart(false);
        }

        boolean inStart = deployment.isInStartZone(to);
        if (inStart && !session.isInsideStart()) {
            if (session.isSkipResetAtStartOnce()) {
                session.setSkipResetAtStartOnce(false);
                if (!session.isStarted()) {
                    session.startTimer(System.currentTimeMillis());
                    player.sendTitle(map.getDifficulty().getTitleColor() + map.getTitle(),
                            map.getSubtitle() != null ? map.getSubtitle() : "", 10, 40, 10);
                }
            } else {
                session.resetTimer();
                session.startTimer(System.currentTimeMillis());
                player.sendTitle(map.getDifficulty().getTitleColor() + map.getTitle(),
                        map.getSubtitle() != null ? map.getSubtitle() : "", 10, 40, 10);
            }
            session.setPendingTitleAtStart(false);
            session.setInsideStart(true);
        } else if (!inStart && session.isInsideStart()) {
            session.setInsideStart(false);
        }

        if (deployment.isInEndZone(to)) {
            sessionManager.endSession(player, true);
        }
    }

    // ============ Non-PKW simple single-level logic ============

    private void handleSimpleMove(Player player, Location from, Location to) {
        // Segment special mode: quit active session and stay silent
        if (isInSegmentSpecialMode(player)) {
            ParkourSession s = sessionManager.getSession(player.getUniqueId());
            if (s != null) {
                sessionManager.endSession(player, false);
            }
            return;
        }

        ParkourSession session = sessionManager.getSession(player.getUniqueId());

        // No session: auto-start when entering a LEVEL's region
        if (session == null) {
            for (ParkourMap map : mapManager.getMaps().values()) {
                if (map.getNodeType() != NodeType.LEVEL) continue;
                for (Deployment dep : map.getDeployments()) {
                    Region r = dep.getRegion();
                    if (r != null && r.contains(to)) {
                        String current = insideDeployment.get(player.getUniqueId());
                        if (current == null || !current.equals(dep.getId())) {
                            sessionManager.startSession(player, map, dep);
                        }
                        insideDeployment.put(player.getUniqueId(), dep.getId());
                        return;
                    }
                }
            }
            insideDeployment.remove(player.getUniqueId());
            return;
        }

        // Only LEVEL sessions are valid in simple mode
        ParkourMap map = mapManager.getMap(session.getMapId());
        if (map == null || map.getNodeType() != NodeType.LEVEL) {
            sessionManager.endSession(player, false);
            return;
        }
        Deployment deployment = map.getDeployment(session.getDeploymentId());
        if (deployment == null) {
            sessionManager.endSession(player, false);
            return;
        }

        Region region = deployment.getRegion();
        if (region == null) return;

        // Rectangular region fallback: fall below region → back to start
        if (to.getBlockY() < region.getMinY()) {
            sessionManager.teleportToCheckpoint(player);
            return;
        }

        // Inside/outside tracking
        boolean wasInside = session.isInsideRegion();
        boolean inside = region.contains(to);
        if (inside) insideDeployment.put(player.getUniqueId(), deployment.getId());
        else insideDeployment.remove(player.getUniqueId());

        if (!wasInside && inside) {
            session.setInsideRegion(true);
        } else if (wasInside && !inside) {
            if (!session.isCompleted() && session.isStarted()) session.stopTimer(System.currentTimeMillis());
            session.setInsideRegion(false);
            session.setInsideStart(false);
        }

        // Start zone: begin timer + show title
        boolean inStart = deployment.isInStartZone(to);
        if (inStart && !session.isInsideStart()) {
            if (!session.isStarted()) {
                session.startTimer(System.currentTimeMillis());
            }
            player.sendTitle(map.getDifficulty().getTitleColor() + map.getTitle(),
                    map.getSubtitle() != null ? map.getSubtitle() : "", 10, 40, 10);
            session.setInsideStart(true);
        } else if (!inStart && session.isInsideStart()) {
            session.setInsideStart(false);
        }

        // End zone: finish level
        if (deployment.isInEndZone(to)) {
            sessionManager.endSession(player, true);
        }
    }

    /**
     * Full-course timing: GLOBAL_START start-point and GLOBAL_END end-point
     * detection. Independent of session state — runs on every cross-block move.
     */
    private void checkFullCourseTiming(Player player, Location to) {
        String worldName = to.getWorld().getName();
        club.sitmc.sitParkourWarrior.config.TimingMode mode = pkwWorldManager.getTimingMode(to.getWorld());

        for (ParkourMap map : mapManager.getMaps().values()) {
            NodeType type = map.getNodeType();
            if (type != NodeType.GLOBAL_START && type != NodeType.GLOBAL_END) {
                continue;
            }
            for (Deployment dep : map.getDeployments()) {
                if (type == NodeType.GLOBAL_START) {
                    if (dep.isInStartZone(to)) {
                        if (mode == club.sitmc.sitParkourWarrior.config.TimingMode.COUNTDOWN) {
                            sessionManager.startFullCourse(player.getUniqueId());
                        } else {
                            sessionManager.startFullCourse(player.getUniqueId());
                        }
                        return;
                    }
                } else {
                    if (dep.isInEndZone(to)) {
                        if (sessionManager.getRunProgress(player.getUniqueId()) != null) {
                            String endTier = map.getEndTier().toConfigString();
                            if (mode == club.sitmc.sitParkourWarrior.config.TimingMode.COUNTDOWN) {
                                sessionManager.finishCountdownByEnd(player, worldName, endTier);
                            } else {
                                sessionManager.finishFullCourse(player, map.getId(), dep.getId(), endTier);
                            }
                            sessionManager.endSession(player, false);
                            sessionManager.cleanupPlayerEquipment(player);
                            player.setFallDistance(0f);
                            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                            player.teleport(to.getWorld().getSpawnLocation());
                        } else {
                            Msg.send(player, "你未从全局起点出发，无法结算全程计时。");
                        }
                        return;
                    }
                }
            }
        }
    }

    private void checkCountdownTimeout(Player player, ParkourSession session) {
        if (session == null) return;
        String worldName = player.getWorld().getName();
        if (pkwWorldManager.getTimingMode(player.getWorld()) != club.sitmc.sitParkourWarrior.config.TimingMode.COUNTDOWN) return;
        if (sessionManager.getRunProgress(player.getUniqueId()) == null) return;
        if (!sessionManager.isCountdownExpired(player.getUniqueId(), worldName)) return;

        sessionManager.finishCountdownByTimeout(player, worldName);
        sessionManager.endSession(player, false);
        sessionManager.cleanupPlayerEquipment(player);
        player.setFallDistance(0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.teleport(player.getWorld().getSpawnLocation());
    }

    /**
     * BRANCH_END teleport: if the player is near a BRANCH_END's end point,
     * look up its bound FORK from the course layout, switch the session to
     * that FORK, and teleport the player to the fork-point.
     *
     * @return true if a BRANCH_END teleport was executed
     */
    private boolean handleBranchEndTeleport(Player player, Location to) {
        String worldName = to.getWorld().getName();
        for (ParkourMap map : mapManager.getMaps().values()) {
            if (map.getNodeType() != NodeType.BRANCH_END) continue;
            for (Deployment dep : map.getDeployments()) {
                if (!dep.isInEndZone(to)) continue;

                // Found a BRANCH_END end point nearby — look up binding.
                CourseLayoutAnalyzer.BranchBindingInfo binding = courseLayoutAnalyzer.getBranchBinding(
                        worldName, map.getId(), dep.getId());
                if (binding == null) {
                    // No binding: safe fallback — teleport to current checkpoint if any.
                    ParkourSession session = sessionManager.getSession(player.getUniqueId());
                    if (session != null) {
                        sessionManager.teleportToCheckpoint(player);
                    }
                    return true;
                }

                // Resolve the bound FORK deployment.
                ParkourMap forkMap = mapManager.getMap(binding.forkMapId);
                if (forkMap == null || forkMap.getNodeType() != NodeType.FORK) return true;
                Deployment forkDep = forkMap.getDeployment(binding.forkDepId);
                if (forkDep == null) return true;

                sessionManager.handoffToFork(player, forkMap, forkDep, binding.forkPoint);
                return true;
            }
        }
        return false;
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
     * Return the entry-point locations for a deployment that can receive handoff.
     * LEVEL → start; FORK → each resolved fork point;
     * BRANCH_END → end (but intercepted by handleBranchEndTeleport before handoff).
     * GLOBAL_START and GLOBAL_END are not handoff targets.
     */
    private List<Location> getEntryPoints(ParkourMap map, Deployment dep) {
        List<Location> points = new ArrayList<>();
        NodeType type = map.getNodeType();
        if (type == NodeType.GLOBAL_START || type == NodeType.GLOBAL_END) {
            return points;
        }
        Region region = dep.getRegion();
        if (type == NodeType.FORK) {
            points.addAll(CourseLinker.resolveForkBranchPoints(map, dep));
        } else if (type == NodeType.BRANCH_END) {
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

    private boolean isLava(Location loc) {
        Material type = loc.getBlock().getType();
        return type == Material.LAVA;
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

    /**
     * Find a deployment whose entry point is near the given location.
     * LEVEL/GLOBAL_START → start zone, FORK → each fork point.
     * GLOBAL_END/BRANCH_END are not entry points (destinations only).
     */
    private DeploymentMatch findDeploymentByEntryPoint(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (ParkourMap map : mapManager.getMaps().values()) {
            NodeType type = map.getNodeType();
            if (type == NodeType.GLOBAL_END || type == NodeType.BRANCH_END) continue;
            for (Deployment dep : map.getDeployments()) {
                if (type == NodeType.FORK) {
                    List<Location> fps = CourseLinker.resolveForkBranchPoints(map, dep);
                    for (Location fp : fps) {
                        if (isNear(location, fp)) return new DeploymentMatch(map, dep);
                    }
                } else {
                    if (dep.isInStartZone(location)) return new DeploymentMatch(map, dep);
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
