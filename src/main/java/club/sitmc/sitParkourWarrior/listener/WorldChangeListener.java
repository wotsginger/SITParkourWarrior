package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.records.RecordsManager;
import club.sitmc.sitParkourWarrior.session.RunProgress;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.util.ItemUtil;
import club.sitmc.sitParkourWarrior.visibility.VisibilityManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.ArrayList;

/**
 * Gives/removes PKW items on world change, and saves active runs when
 * leaving a PKW world to prevent cross-world state leakage.
 */
public class WorldChangeListener implements Listener {

    private final PkwWorldManager pkwWorldManager;
    private final SessionManager sessionManager;
    private final RecordsManager recordsManager;
    private final VisibilityManager visibilityManager;

    public WorldChangeListener(PkwWorldManager pkwWorldManager,
                               SessionManager sessionManager, RecordsManager recordsManager,
                               VisibilityManager visibilityManager) {
        this.pkwWorldManager = pkwWorldManager;
        this.sessionManager = sessionManager;
        this.recordsManager = recordsManager;
        this.visibilityManager = visibilityManager;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean fromPkw = pkwWorldManager.isPkwWorld(event.getFrom());
        boolean toPkw = pkwWorldManager.isPkwWorld(player.getWorld());

        if (fromPkw) {
            visibilityManager.cleanupPlayer(player);
            // Leaving a PKW world: pause and save active run, then clean up
            RunProgress rp = sessionManager.getRunProgress(player.getUniqueId());
            if (rp != null) {
                rp.pause();
                recordsManager.saveActiveRunFull(event.getFrom().getName(),
                        player.getUniqueId(), player.getName(),
                        rp.getElapsedMs(), rp.getMedals(),
                        rp.getStoneCount(), rp.getBronzeCount(), rp.getSilverCount(), rp.getGoldCount(),
                        rp.getClaimedLevelsWithTypes());
                sessionManager.removeRunProgress(player.getUniqueId());
            }
            sessionManager.endSession(player, false);
            removePkwItems(player);
        }

        if (toPkw) {
            givePkwItems(player);
            // Try to restore a saved active run in this world
            RecordsManager.SavedRunData saved = recordsManager.loadAndClearActiveRun(
                    player.getWorld().getName(), player.getUniqueId());
            if (saved != null) {
                RunProgress rp = new RunProgress();
                rp.restoreFull(saved.elapsedMs, saved.medals,
                        saved.stone, saved.bronze, saved.silver, saved.gold,
                        saved.claimedLevels);
                sessionManager.restoreRunProgress(player.getUniqueId(), rp);
            }
        }
    }

    public static void givePkwItems(Player player) {
        if (!hasPkwItem(player, ItemUtil.KEY_FORK_RETURN)) {
            player.getInventory().setItem(0, ItemUtil.createForkReturnItem());
        }
        if (!hasPkwItem(player, ItemUtil.KEY_QUIT)) {
            player.getInventory().setItem(8, ItemUtil.createQuitItem());
        }
    }

    public static void removePkwItems(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            var item = inv.getItem(i);
            if (ItemUtil.isPkwItem(item)) {
                inv.setItem(i, null);
            }
        }
    }

    private static boolean hasPkwItem(Player player, String key) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            var item = inv.getItem(i);
            if (item != null && (key.equals(ItemUtil.KEY_FORK_RETURN) ? ItemUtil.isForkReturnItem(item) : ItemUtil.isQuitItem(item))) {
                return true;
            }
        }
        return false;
    }
}
