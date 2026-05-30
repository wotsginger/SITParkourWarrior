package club.sitmc.sitParkourWarrior.config;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages config.yml — PKW-world list with timing modes and countdown durations.
 */
public class PkwWorldManager {

    private final File configFile;
    private final Map<String, TimingMode> worldModes = new LinkedHashMap<>();
    private final Map<String, Integer> durations = new LinkedHashMap<>();

    public PkwWorldManager(File dataFolder) {
        this.configFile = new File(dataFolder, "config.yml");
        load();
    }

    // ---- persistence ----

    private void load() {
        worldModes.clear();
        durations.clear();
        if (!configFile.exists()) {
            save();
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // pkw-worlds: either a map (new) or a list (old compat)
        ConfigurationSection worldsSection = config.getConfigurationSection("pkw-worlds");
        if (worldsSection != null) {
            for (String key : worldsSection.getKeys(false)) {
                worldModes.put(key, TimingMode.fromString(worldsSection.getString(key)));
            }
        } else {
            // Old format: list of world names → default COUNTUP
            List<String> list = config.getStringList("pkw-worlds");
            for (String name : list) {
                worldModes.put(name, TimingMode.COUNTUP);
            }
        }

        // pkw-world-durations: world → seconds
        ConfigurationSection durSection = config.getConfigurationSection("pkw-world-durations");
        if (durSection != null) {
            for (String key : durSection.getKeys(false)) {
                try {
                    durations.put(key, Integer.parseInt(durSection.getString(key, "0")));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();

        Map<String, String> modeMap = new LinkedHashMap<>();
        for (Map.Entry<String, TimingMode> e : worldModes.entrySet()) {
            modeMap.put(e.getKey(), e.getValue().toConfigString());
        }
        config.set("pkw-worlds", modeMap);

        if (!durations.isEmpty()) {
            Map<String, Object> durMap = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : durations.entrySet()) {
                durMap.put(e.getKey(), e.getValue());
            }
            config.set("pkw-world-durations", durMap);
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            System.err.println("[SITPKW] Failed to save config.yml: " + e.getMessage());
        }
    }

    // ---- query ----

    public boolean isPkwWorld(String worldName) {
        return worldName != null && worldModes.containsKey(worldName);
    }

    public boolean isPkwWorld(World world) {
        return world != null && isPkwWorld(world.getName());
    }

    public TimingMode getTimingMode(String worldName) {
        TimingMode mode = worldModes.get(worldName);
        return mode != null ? mode : TimingMode.COUNTUP;
    }

    public TimingMode getTimingMode(World world) {
        return world != null ? getTimingMode(world.getName()) : TimingMode.COUNTUP;
    }

    public Set<String> getWorlds() {
        return Collections.unmodifiableSet(worldModes.keySet());
    }

    public int getCountdownDuration(String worldName) {
        Integer d = durations.get(worldName);
        return d != null ? d : 0;
    }

    // ---- mutations ----

    public boolean add(String worldName, TimingMode mode) {
        if (worldName == null || worldName.isBlank()) return false;
        if (worldModes.containsKey(worldName)) return false;
        worldModes.put(worldName, mode != null ? mode : TimingMode.COUNTUP);
        save();
        return true;
    }

    public boolean setMode(String worldName, TimingMode mode) {
        if (worldName == null || !worldModes.containsKey(worldName)) return false;
        worldModes.put(worldName, mode != null ? mode : TimingMode.COUNTUP);
        save();
        return true;
    }

    public void setCountdownDuration(String worldName, int seconds) {
        durations.put(worldName, Math.max(1, seconds));
        save();
    }

    public boolean remove(String worldName) {
        if (worldName == null) return false;
        boolean removed = worldModes.remove(worldName) != null;
        durations.remove(worldName);
        if (removed) save();
        return removed;
    }
}
