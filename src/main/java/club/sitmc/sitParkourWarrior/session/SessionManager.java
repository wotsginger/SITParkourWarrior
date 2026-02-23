package club.sitmc.sitParkourWarrior.session;

import club.sitmc.sitParkourWarrior.SITParkourWarrior;
import club.sitmc.sitParkourWarrior.map.Deployment;
import club.sitmc.sitParkourWarrior.map.DynamicService;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.ParkourMap;
import club.sitmc.sitParkourWarrior.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
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
        if (deployment.getStart() == null || deployment.getEnd() == null || deployment.getRegion() == null) {
            Msg.send(player, "关卡配置不完整（需要区域、起点、终点）。");
            return false;
        }
        ParkourSession session = new ParkourSession(player.getUniqueId(), map.getId(), deployment.getId(), System.currentTimeMillis());
        sessions.put(player.getUniqueId(), session);
        dynamicService.onPlayerJoin(map, deployment);
        if (deployment.getRegion() != null && deployment.getRegion().contains(player.getLocation())) {
            session.startTimer(System.currentTimeMillis());
            session.setInsideRegion(true);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            ParkourSession current = sessions.get(player.getUniqueId());
            if (current == null) {
                return;
            }
            if (deployment.getRegion() != null && deployment.getRegion().contains(player.getLocation())) {
                if (!current.isSuppressNextTitle()) {
                    player.sendTitle(
                            map.getDifficulty().getTitleColor() + map.getTitle(),
                            "",
                            10, 40, 10
                    );
                } else {
                    current.setSuppressNextTitle(false);
                }
                current.setInsideRegion(true);
            }
        });
        return true;
    }

    public void endSession(Player player, boolean completed) {
        if (player == null) {
            return;
        }
        ParkourSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        ParkourMap map = mapManager.getMap(session.getMapId());
        Deployment deployment = getDeployment(map, session.getDeploymentId());
        if (map != null && deployment != null) {
            dynamicService.onPlayerLeave(map, deployment);
        }
        if (completed) {
            long durationMs = session.getElapsedMs(System.currentTimeMillis());
            double seconds = durationMs / 1000.0;
            if (map != null) {
                String coloredTitle = map.getDifficulty().getTitleColor() + map.getTitle();
                Msg.send(player, "您已通过 " + coloredTitle + "（用时：" + String.format("%.2f", seconds) + " 秒）。");
            } else {
                Msg.send(player, "您已通过关卡（用时：" + String.format("%.2f", seconds) + " 秒）。");
            }
        } else {
            Msg.send(player, "已结束关卡。");
        }
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
        Location start = deployment.getStart();
        if (start != null) {
            session.setSuppressNextTitle(true);
            session.setInsideRegion(true);
            session.setSkipResetAtStartOnce(true);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.getInventory().setBoots(null);
            player.teleport(start);
        }
    }

    private Deployment getDeployment(ParkourMap map, String deploymentId) {
        if (map == null || deploymentId == null) {
            return null;
        }
        return map.getDeployment(deploymentId);
    }
}
