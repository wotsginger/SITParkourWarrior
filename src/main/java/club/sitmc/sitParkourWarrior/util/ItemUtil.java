package club.sitmc.sitParkourWarrior.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;

/**
 * PDC-based creation and identification of PKW interactive items.
 */
public final class ItemUtil {

    public static final String KEY_FORK_RETURN = "pkw_fork_return";
    public static final String KEY_QUIT = "pkw_quit";

    private static NamespacedKey forkReturnKey;
    private static NamespacedKey quitKey;

    private ItemUtil() {}

    public static void init(JavaPlugin plugin) {
        forkReturnKey = new NamespacedKey(plugin, KEY_FORK_RETURN);
        quitKey = new NamespacedKey(plugin, KEY_QUIT);
    }

    public static ItemStack createForkReturnItem() {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a返回岔路口");
        meta.setLore(Collections.singletonList("§7返回最近岔路口"));
        meta.getPersistentDataContainer().set(forkReturnKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createQuitItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c放弃跑酷");
        meta.setLore(Collections.singletonList("§7放弃当前跑酷"));
        meta.getPersistentDataContainer().set(quitKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isForkReturnItem(ItemStack item) {
        return hasPdc(item, forkReturnKey);
    }

    public static boolean isQuitItem(ItemStack item) {
        return hasPdc(item, quitKey);
    }

    public static boolean isPkwItem(ItemStack item) {
        return isForkReturnItem(item) || isQuitItem(item);
    }

    private static boolean hasPdc(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
