package club.sitmc.sitParkourWarrior.session;

import club.sitmc.sitParkourWarrior.SITParkourWarrior;
import club.sitmc.sitParkourWarrior.map.Deployment;
import club.sitmc.sitParkourWarrior.map.DynamicService;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.NodeType;
import club.sitmc.sitParkourWarrior.map.ParkourMap;
import club.sitmc.sitParkourWarrior.map.Region;
import club.sitmc.sitParkourWarrior.util.Msg;
import org.bukkit.Bukkit;
import club.sitmc.sitParkourWarrior.map.PointLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
    private final SITParkourWarrior plugin;
    private final MapManager mapManager;
    private final DynamicService dynamicService;
    private final Map<UUID, ParkourSession> sessions = new HashMap<>();

    public SessionManager(SITParkourWarrior plugin, MapManager mapManager, DynamicService dynamicService) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.dynamicService = dynamicService;
    }

    public ParkourSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean isPlaying(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean startSession(Player player, ParkourMap map, Deployment deployment) {
        if (player == null || map == null || deployment == null) {
            return false;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            Msg.send(player, "你已经在游玩中，先退出当前关卡。");
            return false;
        }

        Region region = deployment.getRegion();
        Location start = resolveLocationWorld(deployment.getStart(), region);
        Location end = resolveLocationWorld(deployment.getEnd(), region);
        if (start == null || end == null || region == null) {
            Msg.send(player, "关卡配置不完整（需要区域、起点、终点）。");
            return false;
        }

        ParkourSession session = new ParkourSession(player.getUniqueId(), map.getId(), deployment.getId(), System.currentTimeMillis());
        session.setDeathLineY(region.getMinY());
        session.setCheckpoint(start.clone());
        sessions.put(player.getUniqueId(), session);
        dynamicService.onPlayerJoin(map, deployment);

        if (map.getNodeType() == NodeType.LEVEL && region.contains(player.getLocation())) {
            session.startTimer(System.currentTimeMillis());
            session.setInsideRegion(true);
            session.setInsideStart(false);
            session.setPendingTitleAtStart(true);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            ParkourSession current = sessions.get(player.getUniqueId());
            if (current == null) {
                return;
            }
            if (map.getNodeType() == NodeType.LEVEL && region.contains(player.getLocation())) {
                current.setInsideRegion(true);
                current.setInsideStart(false);
                current.setPendingTitleAtStart(true);
            }
        });
        return true;
    }

    public void endSession(Player player, boolean completed) {
        if (player == null) {
            return;
        }
        if (completed) {
            // Completion: keep session alive, transition to waiting-for-handoff state.
            ParkourSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                return;
            }
            if (session.isStarted()) {
                session.stopTimer(System.currentTimeMillis());
            }
            long durationMs = session.getElapsedMs(System.currentTimeMillis());
            double seconds = durationMs / 1000.0;
            ParkourMap map = mapManager.getMap(session.getMapId());
            if (map != null) {
                String coloredTitle = map.getDifficulty().getTitleColor() + map.getTitle();
                Msg.send(player, "您已通过 " + coloredTitle + "（用时：" + String.format("%.2f", seconds) + " 秒）。");
            } else {
                Msg.send(player, "您已通过关卡（用时：" + String.format("%.2f", seconds) + " 秒）。");
            }
            session.setCompleted(true);
            session.setState(SessionState.AWAITING_HANDOFF);
            // Keep deathLineY unchanged (stay at completed node's region minY).
            // Set checkpoint to this level's end.
            Deployment deployment = getDeployment(map, session.getDeploymentId());
            if (deployment != null) {
                Location end = resolveLocationWorld(deployment.getEnd(), deployment.getRegion());
                if (end != null) {
                    session.setCheckpoint(end.clone());
                }
            }
            cleanupPlayerEquipment(player);
            return;
        }

        // Non-completion: fully remove session.
        ParkourSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        ParkourMap map = mapManager.getMap(session.getMapId());
        Deployment deployment = getDeployment(map, session.getDeploymentId());
        if (map != null && deployment != null) {
            dynamicService.onPlayerLeave(map, deployment);
        }
        Msg.send(player, "已结束关卡。");
        session.resetTimer();
    }

    public void endSessionsForMap(String mapId) {
        if (mapId == null) {
            return;
        }
        for (UUID playerId : new java.util.ArrayList<>(sessions.keySet())) {
            ParkourSession session = sessions.get(playerId);
            if (session != null && session.getMapId().equalsIgnoreCase(mapId)) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    endSession(player, false);
                } else {
                    sessions.remove(playerId);
                }
            }
        }
    }

    public void endSessionsForDeployment(String mapId, String deploymentId) {
        if (mapId == null || deploymentId == null) {
            return;
        }
        for (UUID playerId : new java.util.ArrayList<>(sessions.keySet())) {
            ParkourSession session = sessions.get(playerId);
            if (session != null && session.getMapId().equalsIgnoreCase(mapId)
                    && deploymentId.equals(session.getDeploymentId())) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    endSession(player, false);
                } else {
                    sessions.remove(playerId);
                }
            }
        }
    }

    public void endAll() {
        for (UUID playerId : new java.util.ArrayList<>(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                endSession(player, false);
            } else {
                sessions.remove(playerId);
            }
        }
    }

    public void teleportToStart(Player player) {
        ParkourSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        ParkourMap map = mapManager.getMap(session.getMapId());
        Deployment deployment = getDeployment(map, session.getDeploymentId());
        if (deployment == null) {
            return;
        }
        Location start = resolveLocationWorld(deployment.getStart(), deployment.getRegion());
        if (start != null) {
            session.setInsideRegion(true);
            session.setInsideStart(false);
            session.setSkipResetAtStartOnce(true);
            teleportToStartLocation(player, start);
        }
    }

    public void teleportToStartLocation(Player player, Location start) {
        if (player == null || start == null || start.getWorld() == null) {
            return;
        }
        cleanupPlayerEquipment(player);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(start);
    }

    private void cleanupPlayerEquipment(Player player) {
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.getInventory().setBoots(null);
    }

    /**
     * Teleport player to current checkpoint. Sets skipResetAtStartOnce so
     * reappearing at a start area does not reset the timer.
     */
    public void teleportToCheckpoint(Player player) {
        ParkourSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Location checkpoint = session.getCheckpoint();
        if (checkpoint == null) {
            return;
        }
        session.setSkipResetAtStartOnce(true);
        session.setInsideRegion(true);
        session.setInsideStart(false);
        teleportToStartLocation(player, checkpoint);
    }

    /**
     * Transition the session to the next deployment when the player enters
     * its entry point (handoff). Updates deathLine, checkpoint, and resets
     * timer state for LEVEL nodes.
     *
     * @param nextMap        the next deployment's ParkourMap
     * @param nextDeployment the next deployment
     * @param entryPoint     the entry-point location the player reached
     */
    public void handoffToNextDeployment(Player player, ParkourSession session,
                                        ParkourMap nextMap, Deployment nextDeployment,
                                        Location entryPoint) {
        // Update session to point to the new deployment.
        ParkourSession updated = new ParkourSession(
                player.getUniqueId(),
                nextMap.getId(),
                nextDeployment.getId(),
                System.currentTimeMillis());
        updated.setCheckpoint(entryPoint.clone());
        Region nextRegion = nextDeployment.getRegion();
        if (nextRegion != null) {
            updated.setDeathLineY(nextRegion.getMinY());
        }
        sessions.put(player.getUniqueId(), updated);
        dynamicService.onPlayerJoin(nextMap, nextDeployment);

        if (nextMap.getNodeType() == NodeType.FORK) {
            // Record the fork point entered and store previous-level end as initial fallback.
            updated.getVisitedForkPoints().add(entryPoint.clone());
            updated.setInitialForkFallback(session.getCheckpoint());
        } else if (nextMap.getNodeType() == NodeType.LEVEL && nextRegion != null
                && nextRegion.contains(player.getLocation())) {
            updated.startTimer(System.currentTimeMillis());
            updated.setInsideRegion(true);
            updated.setPendingTitleAtStart(true);
        }
    }

    /**
     * FORK death fallback: teleport to nearest visited fork point, or initial
     * fallback (previous-level end) if no fork points have been visited yet.
     */
    public void handleForkFallback(Player player, ParkourSession session) {
        List<Location> visited = session.getVisitedForkPoints();
        if (!visited.isEmpty()) {
            Location nearest = findNearest(player.getLocation(), visited);
            if (nearest != null) {
                session.setSkipResetAtStartOnce(true);
                session.setInsideRegion(true);
                session.setInsideStart(false);
                teleportToStartLocation(player, nearest.clone());
            }
        } else {
            Location fallback = session.getInitialForkFallback();
            if (fallback != null) {
                session.setSkipResetAtStartOnce(true);
                session.setInsideRegion(true);
                session.setInsideStart(false);
                teleportToStartLocation(player, fallback.clone());
            }
        }
    }

    private Location findNearest(Location from, List<Location> candidates) {
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Location loc : candidates) {
            if (loc.getWorld() == null || from.getWorld() == null) {
                continue;
            }
            if (!loc.getWorld().getName().equals(from.getWorld().getName())) {
                continue;
            }
            double d = from.distance(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    private Deployment getDeployment(ParkourMap map, String deploymentId) {
        if (map == null || deploymentId == null) {
            return null;
        }
        return map.getDeployment(deploymentId);
    }

    public Location resolveLocationWorld(PointLocation point, Region region) {
        if (point == null) {
            return null;
        }
        String worldName = point.hasWorld() ? point.getWorldName() : (region != null ? region.getWorldName() : null);
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, point.getX(), point.getY(), point.getZ(), point.getYaw(), point.getPitch());
    }
}
