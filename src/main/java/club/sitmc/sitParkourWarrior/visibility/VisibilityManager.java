package club.sitmc.sitParkourWarrior.visibility;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.session.RunProgress;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manages proximity-based mutual invisibility + glow for runners in PKW worlds.
 * Uses hysteresis (hide at ≤6, show at >9) to prevent flickering.
 */
public class VisibilityManager {

    private static final double HIDE_DISTANCE = 6.0;
    private static final double SHOW_DISTANCE = 9.0;

    private final JavaPlugin plugin;
    private final PkwWorldManager pkwWorldManager;
    private final SessionManager sessionManager;

    /** Pairs currently hidden from each other. pairKey = min(uuid1, uuid2) + ":" + max(uuid1, uuid2) */
    private final Set<String> hiddenPairs = new HashSet<>();
    /** Players currently glowing. */
    private final Set<UUID> glowing = new HashSet<>();

    public VisibilityManager(JavaPlugin plugin, PkwWorldManager pkwWorldManager, SessionManager sessionManager) {
        this.plugin = plugin;
        this.pkwWorldManager = pkwWorldManager;
        this.sessionManager = sessionManager;
    }

    // ---- Periodic scan (call every ~5 ticks) ----

    public void scanAndUpdate() {
        if (sessionManager == null) return;
        // Collect eligible players (PKW world + has RunProgress)
        List<Player> eligible = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!pkwWorldManager.isPkwWorld(p.getWorld())) continue;
            RunProgress rp = sessionManager.getRunProgress(p.getUniqueId());
            if (rp == null) continue;
            eligible.add(p);
        }

        // Pairwise distance check
        for (int i = 0; i < eligible.size(); i++) {
            for (int j = i + 1; j < eligible.size(); j++) {
                Player a = eligible.get(i);
                Player b = eligible.get(j);
                if (a.getWorld() != b.getWorld()) continue;

                String pairKey = pairKey(a.getUniqueId(), b.getUniqueId());
                double dist = a.getLocation().distance(b.getLocation());

                if (dist <= HIDE_DISTANCE && !hiddenPairs.contains(pairKey)) {
                    // Enter hide state
                    a.hideEntity(plugin, b);
                    b.hideEntity(plugin, a);
                    hiddenPairs.add(pairKey);
                    setGlowing(a, true);
                    setGlowing(b, true);
                } else if (dist > SHOW_DISTANCE && hiddenPairs.contains(pairKey)) {
                    // Exit hide state
                    a.showEntity(plugin, b);
                    b.showEntity(plugin, a);
                    hiddenPairs.remove(pairKey);
                    updateGlowingAfterUnhide(a);
                    updateGlowingAfterUnhide(b);
                }
                // 6 < dist <= 9 → hysteresis: maintain current state
            }
        }
    }

    // ---- Cleanup: a player is no longer eligible ----

    /**
     * Fully restore visibility for a player (quit / complete / leave PKW / edit).
     */
    public void cleanupPlayer(Player player) {
        UUID id = player.getUniqueId();
        List<String> toRemove = new ArrayList<>();

        for (String pairKey : hiddenPairs) {
            if (pairKey.contains(id.toString())) {
                UUID otherId = otherId(pairKey, id);
                Player other = Bukkit.getPlayer(otherId);
                if (other != null) {
                    other.showEntity(plugin, player);
                    player.showEntity(plugin, other);
                    updateGlowingAfterUnhide(other);
                } else {
                    // Other is offline — still show to all online
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showEntity(plugin, player);
                    }
                }
                toRemove.add(pairKey);
            }
        }
        hiddenPairs.removeAll(toRemove);
        setGlowing(player, false);

        // Also show this player to everyone (defense in depth)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != player && !p.canSee(player)) {
                p.showEntity(plugin, player);
            }
            if (p != player && !player.canSee(p)) {
                player.showEntity(plugin, p);
            }
        }
    }

    /** Restore all visibility — call on plugin disable / reload. */
    public void cleanupAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            setGlowing(p, false);
        }
        for (String pairKey : new HashSet<>(hiddenPairs)) {
            String[] parts = pairKey.split(":");
            UUID a = UUID.fromString(parts[0]);
            UUID b = UUID.fromString(parts[1]);
            Player pa = Bukkit.getPlayer(a);
            Player pb = Bukkit.getPlayer(b);
            if (pa != null && pb != null) {
                pa.showEntity(plugin, pb);
                pb.showEntity(plugin, pa);
            }
        }
        hiddenPairs.clear();
        glowing.clear();
    }

    // ---- Helpers ----

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private UUID otherId(String pairKey, UUID id) {
        String idStr = id.toString();
        String[] parts = pairKey.split(":");
        return parts[0].equals(idStr) ? UUID.fromString(parts[1]) : UUID.fromString(parts[0]);
    }

    private void setGlowing(Player player, boolean on) {
        if (on) {
            glowing.add(player.getUniqueId());
            player.setGlowing(true);
        } else {
            glowing.remove(player.getUniqueId());
            player.setGlowing(false);
        }
    }

    /** Remove glow if player is no longer in any hidden pair. */
    private void updateGlowingAfterUnhide(Player player) {
        UUID id = player.getUniqueId();
        for (String pairKey : hiddenPairs) {
            if (pairKey.contains(id.toString())) return; // still in another pair
        }
        setGlowing(player, false);
    }
}
