package club.sitmc.sitParkourWarrior.map;

import club.sitmc.sitParkourWarrior.SITParkourWarrior;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapManager {
    private static final long MAX_REGION_VOLUME = 2_000_000L;

    private final SITParkourWarrior plugin;
    private final Map<String, ParkourMap> maps = new HashMap<>();
    private final File mapsFolder;

    public MapManager(SITParkourWarrior plugin) {
        this.plugin = plugin;
        this.mapsFolder = new File(plugin.getDataFolder(), "maps");
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs();
        }
    }

    public Map<String, ParkourMap> getMaps() {
        return Collections.unmodifiableMap(maps);
    }

    public ParkourMap getMap(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase();
        ParkourMap map = maps.get(key);
        if (map != null) {
            return map;
        }
        String altKey = id.replace("_", " ").toLowerCase();
        return maps.get(altKey);
    }

    public ParkourMap createMap(String id) {
        String key = id.toLowerCase();
        if (maps.containsKey(key)) {
            return null;
        }
        String cleanedId = id.trim();
        ParkourMap map = new ParkourMap(cleanedId);
        maps.put(cleanedId.toLowerCase(), map);
        ensureMapFolder(map);
        return map;
    }

    public boolean saveMap(ParkourMap map) {
        if (map == null) {
            return false;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", map.getId());
        config.set("title", map.getTitle());
        config.set("difficulty", map.getDifficulty().name().toLowerCase());
        config.set("deployed", map.isDeployed());
        config.set("effects.particles", map.isParticlesEnabled());
        config.set("effects.sound", map.isSoundEnabled());

        Region region = map.getRegion();
        if (region != null) {
            ConfigurationSection section = config.createSection("region");
            section.set("world", region.getWorldName());
            section.set("pos1.x", region.getMinX());
            section.set("pos1.y", region.getMinY());
            section.set("pos1.z", region.getMinZ());
            section.set("pos2.x", region.getMaxX());
            section.set("pos2.y", region.getMaxY());
            section.set("pos2.z", region.getMaxZ());
        }

        saveLocation(config, "start", map.getStart());
        saveLocation(config, "end", map.getEnd());

        DynamicData dynamicData = map.getDynamicData();
        ConfigurationSection dynamicSection = config.createSection("dynamic");
        dynamicSection.set("enabled", dynamicData.isEnabled());
        dynamicSection.set("interval_sequence", dynamicData.getIntervalSequence());
        dynamicSection.set("states", dynamicData.getStates());
        dynamicSection.set("state_ids", dynamicData.getStateIds());

        List<Deployment> deployments = new ArrayList<>(map.getDeployments());
        if (!deployments.isEmpty()) {
            ConfigurationSection deploymentsSection = config.createSection("deployments");
            int index = 0;
            for (Deployment deployment : deployments) {
                ConfigurationSection deploymentSection = deploymentsSection.createSection(String.valueOf(index++));
                deploymentSection.set("id", deployment.getId());
                Region depRegion = deployment.getRegion();
                if (depRegion != null) {
                    deploymentSection.set("region.world", depRegion.getWorldName());
                    deploymentSection.set("region.pos1.x", depRegion.getMinX());
                    deploymentSection.set("region.pos1.y", depRegion.getMinY());
                    deploymentSection.set("region.pos1.z", depRegion.getMinZ());
                    deploymentSection.set("region.pos2.x", depRegion.getMaxX());
                    deploymentSection.set("region.pos2.y", depRegion.getMaxY());
                    deploymentSection.set("region.pos2.z", depRegion.getMaxZ());
                }
                saveLocation(deploymentSection, "start", deployment.getStart());
                saveLocation(deploymentSection, "end", deployment.getEnd());
            }
        }

        File mapFolder = ensureMapFolder(map);
        File file = new File(mapFolder, "map.yml");
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save map " + map.getId() + ": " + e.getMessage());
            return false;
        }
    }

    public void saveAll() {
        for (ParkourMap map : maps.values()) {
            saveMap(map);
        }
    }

    public boolean deleteMap(String id) {
        ParkourMap map = getMap(id);
        if (map == null) {
            return false;
        }
        maps.remove(map.getId().toLowerCase());
        File folder = getMapFolder(map);
        return deleteFolder(folder);
    }

    public void loadAll() {
        maps.clear();
        if (!mapsFolder.exists()) {
            return;
        }
        File[] folders = mapsFolder.listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }
        for (File folder : folders) {
            File file = new File(folder, "map.yml");
            if (!file.exists()) {
                continue;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String id = config.getString("id", folder.getName().replace("_", " "));
            ParkourMap map = new ParkourMap(id);
            map.setTitle(config.getString("title", id));
            map.setDifficulty(Difficulty.fromString(config.getString("difficulty", "easy")));
            map.setDeployed(config.getBoolean("deployed", false));
            map.setParticlesEnabled(config.getBoolean("effects.particles", true));
            map.setSoundEnabled(config.getBoolean("effects.sound", true));

            ConfigurationSection regionSection = config.getConfigurationSection("region");
            if (regionSection != null) {
                String worldName = regionSection.getString("world");
                int x1 = regionSection.getInt("pos1.x");
                int y1 = regionSection.getInt("pos1.y");
                int z1 = regionSection.getInt("pos1.z");
                int x2 = regionSection.getInt("pos2.x");
                int y2 = regionSection.getInt("pos2.y");
                int z2 = regionSection.getInt("pos2.z");
                if (worldName != null) {
                    map.setRegion(new Region(worldName, x1, y1, z1, x2, y2, z2));
                }
            }

            map.setStart(loadLocation(config, "start"));
            map.setEnd(loadLocation(config, "end"));

            ConfigurationSection dynamicSection = config.getConfigurationSection("dynamic");
            if (dynamicSection != null) {
                DynamicData dynamicData = map.getDynamicData();
                dynamicData.setEnabled(dynamicSection.getBoolean("enabled", false));
                dynamicData.getIntervalSequence().clear();
                dynamicData.getIntervalSequence().addAll(dynamicSection.getIntegerList("interval_sequence"));
                dynamicData.getStates().clear();
                dynamicData.getStates().addAll(dynamicSection.getStringList("states"));
                dynamicData.getStateIds().clear();
                dynamicData.getStateIds().addAll(dynamicSection.getIntegerList("state_ids"));
            }

            map.clearDeployments();
            ConfigurationSection deploymentsSection = config.getConfigurationSection("deployments");
            if (deploymentsSection != null) {
                for (String key : deploymentsSection.getKeys(false)) {
                    ConfigurationSection deploymentSection = deploymentsSection.getConfigurationSection(key);
                    if (deploymentSection == null) {
                        continue;
                    }
                    String deploymentId = deploymentSection.getString("id", key);
                    ConfigurationSection depRegionSection = deploymentSection.getConfigurationSection("region");
                    Region depRegion = null;
                    if (depRegionSection != null) {
                        String depWorldName = depRegionSection.getString("world");
                        int x1 = depRegionSection.getInt("pos1.x");
                        int y1 = depRegionSection.getInt("pos1.y");
                        int z1 = depRegionSection.getInt("pos1.z");
                        int x2 = depRegionSection.getInt("pos2.x");
                        int y2 = depRegionSection.getInt("pos2.y");
                        int z2 = depRegionSection.getInt("pos2.z");
                        if (depWorldName != null) {
                            depRegion = new Region(depWorldName, x1, y1, z1, x2, y2, z2);
                        }
                    }
                    Location depStart = loadLocation(deploymentSection, "start");
                    Location depEnd = loadLocation(deploymentSection, "end");
                    if (depRegion != null) {
                        map.addDeployment(new Deployment(deploymentId, depRegion, depStart, depEnd));
                    }
                }
            }

            maps.put(id.toLowerCase(), map);
        }
    }

    public boolean isRegionTooLarge(Region region) {
        return region != null && region.volume() > MAX_REGION_VOLUME;
    }

    private void saveLocation(ConfigurationSection section, String path, Location location) {
        if (section == null || location == null || location.getWorld() == null) {
            return;
        }
        section.set(path + ".world", location.getWorld().getName());
        section.set(path + ".x", location.getX());
        section.set(path + ".y", location.getY());
        section.set(path + ".z", location.getZ());
        section.set(path + ".yaw", location.getYaw());
        section.set(path + ".pitch", location.getPitch());
    }

    private Location loadLocation(ConfigurationSection section, String path) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = section.getDouble(path + ".x");
        double y = section.getDouble(path + ".y");
        double z = section.getDouble(path + ".z");
        float yaw = (float) section.getDouble(path + ".yaw");
        float pitch = (float) section.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public File getMapFolder(ParkourMap map) {
        return ensureMapFolder(map);
    }

    private File ensureMapFolder(ParkourMap map) {
        String folderName = map.getId().replace(" ", "_");
        File folder = new File(mapsFolder, folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    private boolean deleteFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return false;
        }
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }
        return folder.delete();
    }
}
