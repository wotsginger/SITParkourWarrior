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
 * Uses in-memory cache; all reads hit the cache, writes update both
 * cache and disk synchronously.
 */
public class RecordsManager {

    private final File file;
    private YamlConfiguration cache;

    public RecordsManager(File dataFolder) {
        this.file = new File(dataFolder, "records.yml");
        this.cache = loadFromDisk();
    }

    /** Reload cache from disk (called on /sitpkw reload). */
    public void reload() {
        this.cache = loadFromDisk();
    }

    // ---------------------------------------------------------------
    // Active-run persistence (disconnect / reconnect)
    // ---------------------------------------------------------------

    public void saveActiveRunFull(String worldName, UUID playerId, String playerName,
                                  long elapsedMs, int medals, int stone, int bronze, int silver, int gold,
                                  Map<String, String> claimedLevels) {
        String base = "worlds." + worldName + ".active." + playerId.toString();
        cache.set(base + ".name", playerName);
        cache.set(base + ".elapsed_ms", elapsedMs);
        cache.set(base + ".medals", medals);
        cache.set(base + ".stone", stone);
        cache.set(base + ".bronze", bronze);
        cache.set(base + ".silver", silver);
        cache.set(base + ".gold", gold);
        if (!claimedLevels.isEmpty()) {
            cache.createSection(base + ".claimed_levels", claimedLevels);
        }
        persist();
    }

    @SuppressWarnings("unchecked")
    public SavedRunData loadAndClearActiveRun(String worldName, UUID playerId) {
        String base = "worlds." + worldName + ".active." + playerId.toString();
        if (!cache.contains(base)) return null;

        String name = cache.getString(base + ".name");
        long elapsedMs = cache.getLong(base + ".elapsed_ms");
        int medals = cache.getInt(base + ".medals");
        int stone = cache.getInt(base + ".stone");
        int bronze = cache.getInt(base + ".bronze");
        int silver = cache.getInt(base + ".silver");
        int gold = cache.getInt(base + ".gold");

        Map<String, String> claimed = new LinkedHashMap<>();
        ConfigurationSection cs = cache.getConfigurationSection(base + ".claimed_levels");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                claimed.put(key, cs.getString(key, "countup"));
            }
        } else {
            List<String> oldList = cache.getStringList(base + ".claimed_levels");
            for (String key : oldList) claimed.put(key, "countup");
        }

        cache.set(base, null);
        persist();
        return new SavedRunData(name, elapsedMs, medals, stone, bronze, silver, gold, claimed);
    }

    public void clearActiveRun(String worldName, UUID playerId) {
        cache.set("worlds." + worldName + ".active." + playerId, null);
        persist();
    }

    // ---------------------------------------------------------------
    // COUNTUP leaderboards (best time)
    // ---------------------------------------------------------------

    public boolean saveRecord(String worldName, String tier, UUID playerId,
                              String playerName, long timeMs, int medals) {
        String base = "worlds." + worldName + "." + tier + "." + playerId.toString();
        long existing = cache.getLong(base + ".time_ms", Long.MAX_VALUE);
        if (timeMs < existing) {
            cache.set(base + ".name", playerName);
            cache.set(base + ".time_ms", timeMs);
            cache.set(base + ".medals", medals);
            persist();
            return true;
        }
        if (!playerName.equals(cache.getString(base + ".name"))) {
            cache.set(base + ".name", playerName);
            persist();
        }
        return false;
    }

    public List<RankEntry> getTop(String worldName, String tier, int limit) {
        ConfigurationSection section = cache.getConfigurationSection("worlds." + worldName + "." + tier);
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
                                        long timeMs, String endTier) {
        String base = "worlds." + worldName + ".countdown." + playerId.toString();
        int existing = cache.getInt(base + ".score", Integer.MIN_VALUE);
        if (score > existing) {
            cache.set(base + ".name", playerName);
            cache.set(base + ".score", score);
            cache.set(base + ".stone", stone);
            cache.set(base + ".bronze", bronze);
            cache.set(base + ".silver", silver);
            cache.set(base + ".gold", gold);
            cache.set(base + ".time_ms", timeMs);
            if (endTier != null) cache.set(base + ".end_tier", endTier);
            persist();
            return true;
        }
        if (!playerName.equals(cache.getString(base + ".name"))) {
            cache.set(base + ".name", playerName);
            persist();
        }
        return false;
    }

    public List<CountdownRankEntry> getCountdownTop(String worldName, int limit) {
        ConfigurationSection section = cache.getConfigurationSection("worlds." + worldName + ".countdown");
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
                String endTier = section.getString(key + ".end_tier");
                entries.add(new CountdownRankEntry(id, name, score, stone, bronze, silver, gold, timeMs, endTier));
            } catch (IllegalArgumentException ignored) {}
        }
        entries.sort((a, b) -> Integer.compare(b.score, a.score));
        if (entries.size() > limit) entries = entries.subList(0, limit);
        return entries;
    }

    public String deleteRecord(String worldName, String tier, String uuidOrName) {
        String base = "worlds." + worldName + "." + tier;
        ConfigurationSection section = cache.getConfigurationSection(base);
        if (section == null) return null;

        if (section.contains(uuidOrName)) {
            String name = section.getString(uuidOrName + ".name");
            cache.set(base + "." + uuidOrName, null);
            persist();
            return name != null ? name : uuidOrName;
        }

        String lower = uuidOrName.toLowerCase();
        for (String key : section.getKeys(false)) {
            String storedName = section.getString(key + ".name");
            if (storedName != null && storedName.toLowerCase().equals(lower)) {
                cache.set(base + "." + key, null);
                persist();
                return storedName;
            }
        }
        return null;
    }

    public java.util.Set<String> getRecordedWorldNames() {
        ConfigurationSection worlds = cache.getConfigurationSection("worlds");
        if (worlds == null) return java.util.Collections.emptySet();
        return worlds.getKeys(false);
    }

    public int[] countPlayerStats(String uuidOrName) {
        int[] counts = new int[3];
        ConfigurationSection worlds = cache.getConfigurationSection("worlds");
        if (worlds == null) return counts;

        String lower = uuidOrName.toLowerCase();
        for (String worldName : worlds.getKeys(false)) {
            for (int t = 0; t < 3; t++) {
                String tier = t == 0 ? "standard" : (t == 1 ? "advance" : "expect");
                ConfigurationSection tierSec = cache.getConfigurationSection("worlds." + worldName + "." + tier);
                if (tierSec == null) continue;
                if (tierSec.contains(uuidOrName)) { counts[t]++; continue; }
                boolean found = false;
                for (String key : tierSec.getKeys(false)) {
                    String stored = tierSec.getString(key + ".name");
                    if (stored != null && stored.toLowerCase().equals(lower)) {
                        found = true; break;
                    }
                }
                if (found) counts[t]++;
            }
        }
        return counts;
    }

    public java.util.List<String> getPlayerNames(String worldName, String tier) {
        java.util.List<String> names = new java.util.ArrayList<>();
        ConfigurationSection section = cache.getConfigurationSection("worlds." + worldName + "." + tier);
        if (section == null) return names;
        for (String key : section.getKeys(false)) {
            String name = section.getString(key + ".name");
            if (name != null) names.add(name);
        }
        return names;
    }

    // ---------------------------------------------------------------
    // File I/O
    // ---------------------------------------------------------------

    private YamlConfiguration loadFromDisk() {
        if (file.exists()) return YamlConfiguration.loadConfiguration(file);
        return new YamlConfiguration();
    }

    private void persist() {
        try { cache.save(file); }
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
        public final String endTier;
        CountdownRankEntry(UUID id, String name, int score, int st, int br, int si, int go, long ms, String endTier) {
            this.playerId = id; this.playerName = name; this.score = score;
            this.stone = st; this.bronze = br; this.silver = si; this.gold = go; this.timeMs = ms;
            this.endTier = endTier;
        }
    }
}