package club.sitmc.sitParkourWarrior.records;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages records.yml — active runs (for disconnect/reconnect) and
 * per-world leaderboards (standard / advance / expect / countdown).
 */
public class RecordsManager {

    private final File file;

    public RecordsManager(File dataFolder) {
        this.file = new File(dataFolder, "records.yml");
    }

    // ---------------------------------------------------------------
    // Active-run persistence (disconnect / reconnect)
    // ---------------------------------------------------------------

    /**
     * Save active run with categorized medals (COUNTDOWN-compatible).
     */
    public void saveActiveRunFull(String worldName, UUID playerId, String playerName,
                                  long elapsedMs, int medals, int stone, int bronze, int silver, int gold,
                                  Map<String, String> claimedLevels) {
        YamlConfiguration config = load();
        String base = "worlds." + worldName + ".active." + playerId.toString();
        config.set(base + ".name", playerName);
        config.set(base + ".elapsed_ms", elapsedMs);
        config.set(base + ".medals", medals);
        config.set(base + ".stone", stone);
        config.set(base + ".bronze", bronze);
        config.set(base + ".silver", silver);
        config.set(base + ".gold", gold);
        if (!claimedLevels.isEmpty()) {
            config.createSection(base + ".claimed_levels", claimedLevels);
        }
        save(config);
    }

    @SuppressWarnings("unchecked")
    public SavedRunData loadAndClearActiveRun(String worldName, UUID playerId) {
        YamlConfiguration config = load();
        String base = "worlds." + worldName + ".active." + playerId.toString();
        if (!config.contains(base)) return null;

        String name = config.getString(base + ".name");
        long elapsedMs = config.getLong(base + ".elapsed_ms");
        int medals = config.getInt(base + ".medals");
        int stone = config.getInt(base + ".stone");
        int bronze = config.getInt(base + ".bronze");
        int silver = config.getInt(base + ".silver");
        int gold = config.getInt(base + ".gold");

        Map<String, String> claimed = new LinkedHashMap<>();
        ConfigurationSection cs = config.getConfigurationSection(base + ".claimed_levels");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                claimed.put(key, cs.getString(key, "countup"));
            }
        } else {
            // Old format: list of keys
            List<String> oldList = config.getStringList(base + ".claimed_levels");
            for (String key : oldList) claimed.put(key, "countup");
        }

        config.set(base, null);
        save(config);

        return new SavedRunData(name, elapsedMs, medals, stone, bronze, silver, gold, claimed);
    }

    public void clearActiveRun(String worldName, UUID playerId) {
        YamlConfiguration config = load();
        config.set("worlds." + worldName + ".active." + playerId, null);
        save(config);
    }

    // ---------------------------------------------------------------
    // COUNTUP leaderboards (best time)
    // ---------------------------------------------------------------

    public boolean saveRecord(String worldName, String tier, UUID playerId,
                              String playerName, long timeMs, int medals) {
        YamlConfiguration config = load();
        String base = "worlds." + worldName + "." + tier + "." + playerId.toString();
        long existing = config.getLong(base + ".time_ms", Long.MAX_VALUE);
        if (timeMs < existing) {
            config.set(base + ".name", playerName);
            config.set(base + ".time_ms", timeMs);
            config.set(base + ".medals", medals);
            save(config);
            return true;
        }
        if (!playerName.equals(config.getString(base + ".name"))) {
            config.set(base + ".name", playerName);
            save(config);
        }
        return false;
    }

    public List<RankEntry> getTop(String worldName, String tier, int limit) {
        YamlConfiguration config = load();
        ConfigurationSection section = config.getConfigurationSection("worlds." + worldName + "." + tier);
        if (section == null) return Collections.emptyList();

        List<RankEntry> entries = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String name = section.getString(key + ".name");
                long timeMs = section.getLong(key + ".time_ms");
                int medals = section.getInt(key + ".medals");
                entries.add(new RankEntry(id, name, timeMs, medals));
            } catch (IllegalArgumentException ignored) {}
        }
        entries.sort(Comparator.comparingLong(e -> e.timeMs));
        if (entries.size() > limit) entries = entries.subList(0, limit);
        return entries;
    }

    // ---------------------------------------------------------------
    // COUNTDOWN leaderboard (best score, descending)
    // ---------------------------------------------------------------

    public boolean saveCountdownRecord(String worldName, UUID playerId,
                                        String playerName, int score,
                                        int stone, int bronze, int silver, int gold,
                                        long timeMs) {
        YamlConfiguration config = load();
        String base = "worlds." + worldName + ".countdown." + playerId.toString();
        int existing = config.getInt(base + ".score", Integer.MIN_VALUE);
        if (score > existing) {
            config.set(base + ".name", playerName);
            config.set(base + ".score", score);
            config.set(base + ".stone", stone);
            config.set(base + ".bronze", bronze);
            config.set(base + ".silver", silver);
            config.set(base + ".gold", gold);
            config.set(base + ".time_ms", timeMs);
            save(config);
            return true;
        }
        if (!playerName.equals(config.getString(base + ".name"))) {
            config.set(base + ".name", playerName);
            save(config);
        }
        return false;
    }

    public List<CountdownRankEntry> getCountdownTop(String worldName, int limit) {
        YamlConfiguration config = load();
        ConfigurationSection section = config.getConfigurationSection("worlds." + worldName + ".countdown");
        if (section == null) return Collections.emptyList();

        List<CountdownRankEntry> entries = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String name = section.getString(key + ".name");
                int score = section.getInt(key + ".score");
                int stone = section.getInt(key + ".stone");
                int bronze = section.getInt(key + ".bronze");
                int silver = section.getInt(key + ".silver");
                int gold = section.getInt(key + ".gold");
                long timeMs = section.getLong(key + ".time_ms");
                entries.add(new CountdownRankEntry(id, name, score, stone, bronze, silver, gold, timeMs));
            } catch (IllegalArgumentException ignored) {}
        }
        entries.sort((a, b) -> Integer.compare(b.score, a.score));
        if (entries.size() > limit) entries = entries.subList(0, limit);
        return entries;
    }

    // ---------------------------------------------------------------
    // File I/O
    // ---------------------------------------------------------------

    private YamlConfiguration load() {
        if (file.exists()) return YamlConfiguration.loadConfiguration(file);
        return new YamlConfiguration();
    }

    private void save(YamlConfiguration config) {
        try { config.save(file); }
        catch (IOException e) { System.err.println("[SITPKW] Failed to save records.yml: " + e.getMessage()); }
    }

    // ---------------------------------------------------------------
    // Data types
    // ---------------------------------------------------------------

    public static class SavedRunData {
        public final String playerName;
        public final long elapsedMs;
        public final int medals;
        public final int stone, bronze, silver, gold;
        public final Map<String, String> claimedLevels;

        SavedRunData(String playerName, long elapsedMs, int medals,
                     int stone, int bronze, int silver, int gold,
                     Map<String, String> claimedLevels) {
            this.playerName = playerName;
            this.elapsedMs = elapsedMs;
            this.medals = medals;
            this.stone = stone;
            this.bronze = bronze;
            this.silver = silver;
            this.gold = gold;
            this.claimedLevels = claimedLevels != null ? claimedLevels : Collections.emptyMap();
        }
    }

    public static class RankEntry {
        public final UUID playerId;
        public final String playerName;
        public final long timeMs;
        public final int medals;
        RankEntry(UUID playerId, String playerName, long timeMs, int medals) {
            this.playerId = playerId; this.playerName = playerName;
            this.timeMs = timeMs; this.medals = medals;
        }
    }

    public static class CountdownRankEntry {
        public final UUID playerId;
        public final String playerName;
        public final int score, stone, bronze, silver, gold;
        public final long timeMs;
        CountdownRankEntry(UUID id, String name, int score, int st, int br, int si, int go, long ms) {
            this.playerId = id; this.playerName = name; this.score = score;
            this.stone = st; this.bronze = br; this.silver = si; this.gold = go; this.timeMs = ms;
        }
    }
}
