package club.sitmc.sitParkourWarrior.visibility;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.session.RunProgress;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Proximity-based invisibility + glow for PKW runners, using potion effects.
 * Hysteresis: buffs at ≤6 blocks, remove at >9 blocks.
 */
public class VisibilityManager {

    private static final double BUFF_DISTANCE = 6.0;
    private static final double CLEAR_DISTANCE = 9.0;
    private static final int EFFECT_DURATION = 15; // ticks, refreshed each scan

    private final JavaPlugin plugin;
    private final PkwWorldManager pkwWorldManager;
    private final SessionManager sessionManager;

    /** Players we've applied effects to (so we only remove our own). */
    private final Set<UUID> managedPlayers = new HashSet<>();

    public VisibilityManager(JavaPlugin plugin, PkwWorldManager pkwWorldManager, SessionManager sessionManager) {
        this.plugin = plugin;
        this.pkwWorldManager = pkwWorldManager;
        this.sessionManager = sessionManager;
    }

    // ---- Periodic scan ----

    public void scanAndUpdate() {
        if (sessionManager == null) return;

        // Collect eligible players
        List<Player> eligible = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!pkwWorldManager.isPkwWorld(p.getWorld())) continue;
            RunProgress rp = sessionManager.getRunProgress(p.getUniqueId());
            if (rp == null) continue;
            eligible.add(p);
        }

        // For each eligible player, check if any other is nearby
        for (Player p : eligible) {
            boolean hasNearby = false;
            for (Player other : eligible) {
                if (other == p) continue;
                if (other.getWorld() != p.getWorld()) continue;
                double dist = p.getLocation().distance(other.getLocation());
                if (dist <= BUFF_DISTANCE) {
                    hasNearby = true;
                    break;
                }
            }

            UUID id = p.getUniqueId();
            if (hasNearby) {
                // Apply / refresh effects
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, EFFECT_DURATION, 0, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, EFFECT_DURATION, 0, false, false, true));
                managedPlayers.add(id);
            } else if (managedPlayers.contains(id)) {
                // Check if far enough to clear (hysteresis)
                boolean allFar = true;
                for (Player other : eligible) {
                    if (other == p) continue;
                    if (other.getWorld() != p.getWorld()) continue;
                    if (p.getLocation().distance(other.getLocation()) <= CLEAR_DISTANCE) {
                        allFar = false;
                        break;
                    }
                }
                if (allFar) {
                    p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    p.removePotionEffect(PotionEffectType.GLOWING);
                    managedPlayers.remove(id);
                }
            }
        }
    }

    // ---- Cleanup ----

    /** Remove our effects from a single player. */
    public void cleanupPlayer(Player player) {
        UUID id = player.getUniqueId();
        if (managedPlayers.remove(id)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
    }

    /** Remove our effects from all players. */
    public void cleanupAll() {
        for (UUID id : new HashSet<>(managedPlayers)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                p.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        managedPlayers.clear();
    }
}
