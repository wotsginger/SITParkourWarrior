package club.sitmc.sitParkourWarrior.listener;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.session.RunProgress;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.visibility.VisibilityManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Saves active run progress (with exact location) when a player is teleported
 * away from their PKW position by an external source (menu, command, other plugin).
 * <p>
 * Internal teleports (death-line respawn, checkpoint return, handoff, fork fallback,
 * fork-return item, quit item) are marked via {@link SessionManager#isInternalTeleport}
 * and skipped here — they are part of normal course mechanics, not a "leave".
 */
public class PlayerTeleportListener implements Listener {

    private final SessionManager sessionManager;
    private final PkwWorldManager pkwWorldManager;
    private final VisibilityManager visibilityManager;

    public PlayerTeleportListener(SessionManager sessionManager, PkwWorldManager pkwWorldManager,
                                   VisibilityManager visibilityManager) {
        this.sessionManager = sessionManager;
        this.pkwWorldManager = pkwWorldManager;
        this.visibilityManager = visibilityManager;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Skip internal teleports (death respawn, checkpoint, handoff, fork fallback, etc.)
        if (sessionManager.isInternalTeleport(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null) return;

        World fromWorld = from.getWorld();
        if (fromWorld == null) return;

        // Only care about teleports originating from a PKW world
        if (!pkwWorldManager.isPkwWorld(fromWorld)) return;

        World toWorld = to.getWorld();
        // Same-world teleport: the run is NOT interrupted — let it continue
        // as-is (global timer keeps running, session state, medals, and
        // in-level progress are all untouched). Only the player's position changes.
        if (toWorld != null && fromWorld.equals(toWorld)) return;

        // Different-world teleport from a PKW world: save and pause the run
        // so it can be resumed when the player returns to the origin world.

        // Nothing to save if the player has no active run or session
        RunProgress rp = sessionManager.getRunProgress(player.getUniqueId());
        boolean hasSession = sessionManager.getSession(player.getUniqueId()) != null;
        if (rp == null && !hasSession) return;

        // Save active run (with session snapshot) and clean up in-memory state
        sessionManager.saveAndClearActiveRun(player);

        // Clean up visibility effects
        visibilityManager.cleanupPlayer(player);

        // Remove PKW items if leaving the PKW world entirely
        if (toWorld == null || !pkwWorldManager.isPkwWorld(toWorld)) {
            WorldChangeListener.removePkwItems(player);
        }
    }
}
