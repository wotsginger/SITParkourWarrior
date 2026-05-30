package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.course.CourseLayoutAnalyzer;
import club.sitmc.sitParkourWarrior.map.CourseLinker;
import club.sitmc.sitParkourWarrior.map.Deployment;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.NodeType;
import club.sitmc.sitParkourWarrior.map.ParkourMap;
import club.sitmc.sitParkourWarrior.session.ParkourSession;
import club.sitmc.sitParkourWarrior.session.RunProgress;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.util.ItemUtil;
import club.sitmc.sitParkourWarrior.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles right-click on PKW interactive items.
 */
public class PkwItemInteractListener implements Listener {

    private final SessionManager sessionManager;
    private final MapManager mapManager;
    private final PkwWorldManager pkwWorldManager;
    private final CourseLayoutAnalyzer courseLayoutAnalyzer;

    public PkwItemInteractListener(SessionManager sessionManager, MapManager mapManager,
                                   PkwWorldManager pkwWorldManager, CourseLayoutAnalyzer courseLayoutAnalyzer) {
        this.sessionManager = sessionManager;
        this.mapManager = mapManager;
        this.pkwWorldManager = pkwWorldManager;
        this.courseLayoutAnalyzer = courseLayoutAnalyzer;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!pkwWorldManager.isPkwWorld(player.getWorld())) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (ItemUtil.isForkReturnItem(item)) {
            event.setCancelled(true);
            handleForkReturn(player);
        } else if (ItemUtil.isQuitItem(item)) {
            event.setCancelled(true);
            handleQuit(player);
        }
    }

    private void handleForkReturn(Player player) {
        ParkourSession session = sessionManager.getSession(player.getUniqueId());
        RunProgress rp = sessionManager.getRunProgress(player.getUniqueId());

        if (session == null) {
            Msg.send(player, "你还没有进行中的跑酷。");
            return;
        }

        ParkourMap map = mapManager.getMap(session.getMapId());
        if (map == null) return;
        Deployment dep = map.getDeployment(session.getDeploymentId());
        if (dep == null) return;

        NodeType nodeType = map.getNodeType();

        // 1) Currently in a FORK → use handleForkFallback (nearest visited fork point)
        if (nodeType == NodeType.FORK) {
            sessionManager.handleForkFallback(player, session);
            return;
        }

        // 2) Currently in a branch LEVEL → teleport to the bound FORK point
        CourseLayoutAnalyzer.LevelRoleInfo role = courseLayoutAnalyzer.getLevelRole(
                player.getWorld().getName(), session.getMapId(), session.getDeploymentId());
        if (role != null && "branch".equals(role.role) && role.branchEndDepId != null) {
            CourseLayoutAnalyzer.BranchBindingInfo binding = courseLayoutAnalyzer.getBranchBinding(
                    player.getWorld().getName(), role.branchEndMapId, role.branchEndDepId);
            if (binding != null && binding.forkPoint != null) {
                sessionManager.teleportToStartLocation(player, binding.forkPoint.clone());
                return;
            }
        }

        // 3) Main line or other → teleport to last visited FORK
        if (rp != null && rp.getLastForkPoint() != null) {
            sessionManager.teleportToStartLocation(player, rp.getLastForkPoint().clone());
            return;
        }

        // 4) No FORK visited yet
        Msg.send(player, "你还没有经过任何岔路口。");
    }

    private void handleQuit(Player player) {
        if (sessionManager.getRunProgress(player.getUniqueId()) == null
                && sessionManager.getSession(player.getUniqueId()) == null) {
            Msg.send(player, "你没有进行中的跑酷。");
            return;
        }
        sessionManager.quitRun(player);
        sessionManager.endSession(player, false);
        sessionManager.cleanupPlayerEquipment(player);
        player.setFallDistance(0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.teleport(player.getWorld().getSpawnLocation());
        Msg.send(player, "已放弃当前跑酷。");
    }
}
