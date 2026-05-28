package club.sitmc.sitParkourWarrior.config;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the global config.yml — currently stores only the PKW-world list.
 */
public class PkwWorldManager {

    private final File configFile;
    private final Set<String> pkwWorlds = new LinkedHashSet<>();

    public PkwWorldManager(File dataFolder) {
        this.configFile = new File(dataFolder, "config.yml");
        load();
    }

    // ---- persistence ----

    private void load() {
        pkwWorlds.clear();
        if (!configFile.exists()) {
            save(); // create default empty file
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        List<String> list = config.getStringList("pkw-worlds");
        if (list != null) {
            pkwWorlds.addAll(list);
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("pkw-worlds", new java.util.ArrayList<>(pkwWorlds));
        try {
            config.save(configFile);
        } catch (IOException e) {
            System.err.println("[SITPKW] Failed to save config.yml: " + e.getMessage());
        }
    }

    // ---- query ----

    public boolean isPkwWorld(String worldName) {
        return worldName != null && pkwWorlds.contains(worldName);
    }

    public boolean isPkwWorld(World world) {
        return world != null && isPkwWorld(world.getName());
    }

    public Set<String> getWorlds() {
        return Collections.unmodifiableSet(pkwWorlds);
    }

    // ---- mutations (auto-persist) ----

    public boolean add(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        if (pkwWorlds.add(worldName)) {
            save();
            return true;
        }
        return false; // already present
    }

    public boolean remove(String worldName) {
        if (worldName == null) {
            return false;
        }
        if (pkwWorlds.remove(worldName)) {
            save();
            return true;
        }
        return false; // not found
    }
}
