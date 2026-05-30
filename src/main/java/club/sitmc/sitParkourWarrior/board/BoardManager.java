package club.sitmc.sitParkourWarrior.board;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Manages leaderboard TextDisplay boards — creation, persistence, lifecycle.
 */
public class BoardManager {

    private static final String PDC_KEY = "pkw_board";

    private final JavaPlugin plugin;
    private final File file;
    private NamespacedKey pdcKey;
    private final List<BoardData> boards = new ArrayList<>();

    /** Set by the main class when recordsManager is available. */
    private Runnable onBoardChanged;

    public BoardManager(JavaPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.file = new File(dataFolder, "boards.yml");
    }

    public void init(NamespacedKey pdcKey) {
        this.pdcKey = pdcKey;
        load();
    }

    public void setOnBoardChanged(Runnable callback) {
        this.onBoardChanged = callback;
    }

    public void notifyBoardChanged() {
        if (onBoardChanged != null) onBoardChanged.run();
    }

    // ---- CRUD ----

    public List<BoardData> getBoards() {
        return Collections.unmodifiableList(boards);
    }

    public List<BoardData> getBoardsInWorld(String worldName) {
        List<BoardData> result = new ArrayList<>();
        for (BoardData b : boards) {
            if (b.getWorldName().equals(worldName)) result.add(b);
        }
        return result;
    }

    /** Create a new board, spawn TextDisplay, persist. */
    public BoardData addBoard(String worldName, String tier, Location loc) {
        BoardData board = new BoardData(worldName, tier, loc);
        spawnEntity(board);
        boards.add(board);
        save();
        return board;
    }

    /** Remove the nearest board to a location. */
    public BoardData removeNearest(Location loc) {
        BoardData best = null;
        double bestDist = Double.MAX_VALUE;
        for (BoardData b : boards) {
            if (!b.getWorldName().equals(loc.getWorld().getName())) continue;
            double d = b.distanceSquared(loc);
            if (d < bestDist) { bestDist = d; best = b; }
        }
        if (best != null) {
            removeEntity(best);
            boards.remove(best);
            save();
        }
        return best;
    }

    // ---- Entity management ----

    public void spawnEntity(BoardData board) {
        Location loc = board.toLocation();
        if (loc == null || loc.getWorld() == null) return;

        // Remove existing entity if UUID is known
        if (board.getEntityUuid() != null) {
            Entity existing = Bukkit.getEntity(board.getEntityUuid());
            if (existing != null) existing.remove();
        }

        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        display.setPersistent(true);
        display.setBillboard(TextDisplay.Billboard.CENTER);
        display.setSeeThrough(false);
        display.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte) 1);
        board.setEntityUuid(display.getUniqueId());
    }

    public TextDisplay getEntity(BoardData board) {
        if (board.getEntityUuid() == null) return null;
        Entity entity = Bukkit.getEntity(board.getEntityUuid());
        if (entity instanceof TextDisplay) return (TextDisplay) entity;
        return null;
    }

    private void removeEntity(BoardData board) {
        Entity entity = Bukkit.getEntity(board.getEntityUuid());
        if (entity != null) entity.remove();
    }

    /**
     * Restore entities after reload/restart. Finds existing by UUID,
     * or re-spawns if entity was lost.
     */
    public void restoreAllEntities() {
        for (BoardData board : boards) {
            if (board.getEntityUuid() != null) {
                Entity existing = Bukkit.getEntity(board.getEntityUuid());
                if (existing instanceof TextDisplay) {
                    // Already exists — mark as ours
                    existing.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte) 1);
                    continue;
                }
            }
            // Entity lost — re-spawn
            spawnEntity(board);
            save();
        }
    }

    // ---- Persistence ----

    private void load() {
        boards.clear();
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection("boards");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String worldName = sec.getString(key + ".world");
            String tier = sec.getString(key + ".tier");
            double x = sec.getDouble(key + ".x");
            double y = sec.getDouble(key + ".y");
            double z = sec.getDouble(key + ".z");
            String uuidStr = sec.getString(key + ".entity_uuid");
            UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
            if (worldName != null && tier != null) {
                boards.add(new BoardData(worldName, tier, x, y, z, uuid));
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < boards.size(); i++) {
            BoardData b = boards.get(i);
            String base = "boards." + i;
            config.set(base + ".world", b.getWorldName());
            config.set(base + ".tier", b.getTier());
            config.set(base + ".x", b.getX());
            config.set(base + ".y", b.getY());
            config.set(base + ".z", b.getZ());
            if (b.getEntityUuid() != null) {
                config.set(base + ".entity_uuid", b.getEntityUuid().toString());
            }
        }
        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Failed to save boards.yml: " + e.getMessage()); }
    }
}
