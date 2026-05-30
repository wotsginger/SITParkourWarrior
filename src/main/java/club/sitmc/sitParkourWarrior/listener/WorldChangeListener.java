package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.util.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Gives PKW items when entering a PKW world, removes them when leaving.
 */
public class WorldChangeListener implements Listener {

    private final PkwWorldManager pkwWorldManager;

    public WorldChangeListener(PkwWorldManager pkwWorldManager) {
        this.pkwWorldManager = pkwWorldManager;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean fromPkw = pkwWorldManager.isPkwWorld(event.getFrom());
        boolean toPkw = pkwWorldManager.isPkwWorld(player.getWorld());

        if (fromPkw && !toPkw) {
            removePkwItems(player);
        } else if (!fromPkw && toPkw) {
            givePkwItems(player);
        }
    }

    public static void givePkwItems(Player player) {
        // Give items only if they don't already exist
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
