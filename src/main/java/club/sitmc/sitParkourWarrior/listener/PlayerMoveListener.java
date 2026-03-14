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
    // 记录玩家当前处于哪个 deployment 区域内，避免重复触发 startSession。
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
        // 仅在“跨方块”移动时处理，减少高频 move 事件带来的开销。
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        // 玩家在编辑选区时，不参与跑酷逻辑。
        if (selectionManager.isEditing(player)) {
            return;
        }
        ParkourSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) {
            // 无会话时：若进入某个 deployment 区域，则自动创建会话。
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
        boolean wasInside = session.isInsideRegion();
        boolean inside = region != null && region.contains(to);
        // 在区域底部(接近 minY)触发回弹：传送回起点，防止玩家掉出关卡。
        boolean hitLowerBounce = region != null
                && !session.isCompleted()
                && to.getWorld() != null
                && region.getWorldName().equals(to.getWorld().getName())
                && wasInside
                && to.getBlockY() <= region.getMinY() + 1;
        if (hitLowerBounce) {
            sessionManager.teleportToStart(player);
            return;
        }
        if (inside) {
            insideDeployment.put(player.getUniqueId(), deployment.getId());
        } else {
            insideDeployment.remove(player.getUniqueId());
        }
        if (!session.isInsideRegion() && inside) {
            // 首次进入区域后，标记待显示标题（真正进入起点范围时展示）。
            session.setInsideStart(false);
            session.setPendingTitleAtStart(true);
            session.setInsideRegion(true);
        } else if (wasInside && !inside) {
            // 离开区域且仍在进行中：中止并重置本次计时。
            if (!session.isCompleted() && session.isStarted()) {
                session.stopTimer(System.currentTimeMillis());
                session.resetTimer();
            }
            sessionManager.clearShoesAndBuff(player);
            sessionManager.endSession(player, false);
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
            // 起点判定使用 1 格容差，提升站位容错。
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
                // 仅首次起跑时开启计时；被回弹传送回起点时不重置当前计时。
                if (!session.isStarted()) {
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
            // 进入终点范围：停止计时并以成功状态结束会话。
            if (dx <= 1 && dy <= 1 && dz <= 1) {
                // 未从起点起跑（计时未开始）时，终点判定无效。
                if (!session.isStarted()) {
                    return;
                }
                if (session.isStarted()) {
                    session.stopTimer(System.currentTimeMillis());
                }
                session.setCompleted(true);
                sessionManager.clearShoesAndBuff(player);
                sessionManager.endSession(player, true);
                return;
            }
        }
    }

    private DeploymentMatch findDeploymentByRegion(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        // 遍历所有地图的 deployment，找到玩家所在区域。
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
