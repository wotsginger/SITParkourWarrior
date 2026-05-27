package club.sitmc.sitParkourWarrior.map;

import club.sitmc.sitParkourWarrior.SITParkourWarrior;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundGroup;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicService {
    private final SITParkourWarrior plugin;
    private final MapManager mapManager;
    private final Map<String, DynamicTask> activeTasks = new HashMap<>();

    public DynamicService(SITParkourWarrior plugin, MapManager mapManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
    }

    public void startAllDeployed() {
        for (ParkourMap map : mapManager.getMaps().values()) {
            for (Deployment deployment : map.getDeployments()) {
                startForDeployment(map, deployment);
            }
        }
    }

    public void startForDeployment(ParkourMap map, Deployment deployment) {
        if (map == null || deployment == null || !isDynamicActive(map)) {
            return;
        }
        String key = buildKey(map.getId(), deployment.getId());
        DynamicTask task = activeTasks.get(key);
        if (task == null) {
            DynamicTask newTask = new DynamicTask(map, deployment);
            activeTasks.put(key, newTask);
            newTask.start();
        }
    }

    public void stopForDeployment(ParkourMap map, Deployment deployment) {
        if (map == null || deployment == null) {
            return;
        }
        String key = buildKey(map.getId(), deployment.getId());
        DynamicTask task = activeTasks.remove(key);
        if (task != null) {
            task.stop();
        }
    }

    public void onPlayerJoin(ParkourMap map, Deployment deployment) {
        // No longer tied to player presence, but keep as a safety start.
        startForDeployment(map, deployment);
    }

    public void onPlayerLeave(ParkourMap map, Deployment deployment) {
        // Do nothing. Dynamic runs while deployed regardless of players.
    }

    public void stopAll() {
        for (DynamicTask task : activeTasks.values()) {
            task.stop();
        }
        activeTasks.clear();
    }

    private String buildKey(String mapId, String deploymentId) {
        return mapId.toLowerCase() + ":" + deploymentId;
    }

    private boolean isDynamicActive(ParkourMap map) {
        if (map == null) {
            return false;
        }
        DynamicData data = map.getDynamicData();
        // Consider dynamic active as long as there are at least 2 states.
        return data.getStates().size() > 1;
    }

    private class DynamicTask {
        private final ParkourMap map;
        private final Deployment deployment;
        private int sequenceIndex = 0;
        private int stateIndex = 0;
        private BukkitTask pendingTask;

        private DynamicTask(ParkourMap map, Deployment deployment) {
            this.map = map;
            this.deployment = deployment;
        }

        public void start() {
            scheduleNext(1);
        }

        public void stop() {
            if (pendingTask != null) {
                pendingTask.cancel();
                pendingTask = null;
            }
        }

        private void scheduleNext(long delayTicks) {
            pendingTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isDynamicActive(map)) {
                    stop();
                    return;
                }
                int duration = playOnce();
                if (duration <= 0) {
                    duration = 1;
                }
                scheduleNext(duration);
            }, delayTicks);
        }

        private int playOnce() {
            DynamicData data = map.getDynamicData();
            List<String> states = data.getStates();
            List<Integer> intervals = data.getIntervalSequence();
            if (states.isEmpty()) {
                return 20;
            }
            while (intervals.size() < states.size()) {
                intervals.add(1);
            }
            while (intervals.size() > states.size()) {
                intervals.remove(intervals.size() - 1);
            }
            if (stateIndex >= states.size()) {
                stateIndex = 0;
            }
            int duration = intervals.get(stateIndex);
            String fileName = states.get(stateIndex);
            stateIndex++;
            pasteSchematic(map, deployment, fileName);
            return duration;
        }
    }

    private void pasteSchematic(ParkourMap map, Deployment deployment, String fileName) {
        if (fileName == null || fileName.isBlank() || deployment == null) {
            return;
        }
        Region region = deployment.getRegion();
        if (region == null) {
            plugin.getLogger().warning("Map " + map.getId() + " deployment has no region, cannot paste.");
            return;
        }
        org.bukkit.World bukkitWorld = Bukkit.getWorld(region.getWorldName());
        if (bukkitWorld == null) {
            return;
        }
        File mapFolder = mapManager.getMapFolder(map);
        File schemFile = new File(mapFolder, fileName);
        if (!schemFile.exists()) {
            plugin.getLogger().warning("Schematic not found: " + schemFile.getAbsolutePath());
            return;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
        if (format == null) {
            plugin.getLogger().warning("Unsupported schematic format: " + fileName);
            return;
        }

        try (FileInputStream fis = new FileInputStream(schemFile); ClipboardReader reader = format.getReader(fis)) {
            Clipboard clipboard = reader.read();
            World weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld);
            Location origin = new Location(bukkitWorld, region.getMinX(), region.getMinY(), region.getMinZ());
            List<BlockChange> changes = collectChanges(clipboard, bukkitWorld, origin);
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .build()) {
                try {
                    // Reduce FAWE/WorldEdit history and disk churn (e.g. clipboard/*.bd).
                    editSession.setTrackingHistory(false);
                } catch (Throwable ignored) {
                    // Older WorldEdit implementations may not support this call.
                }
                tryDisableSideEffects(editSession);

                ClipboardHolder holder = new ClipboardHolder(clipboard);
                Operation operation = holder.createPaste(editSession)
                        .to(BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()))
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(operation);
                editSession.flushSession();
            }
            playChanges(map, deployment.getRegion(), changes);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to paste schematic " + fileName + ": " + e.getMessage());
        } catch (Throwable t) {
            plugin.getLogger().warning("WorldEdit paste error: " + t.getMessage());
        }
    }

    private List<BlockChange> collectChanges(Clipboard clipboard, org.bukkit.World world, Location origin) {
        List<BlockChange> changes = new ArrayList<>();
        BlockVector3 min = clipboard.getRegion().getMinimumPoint();
        for (BlockVector3 vec : clipboard.getRegion()) {
            BlockVector3 offset = vec.subtract(min);
            Location loc = origin.clone().add(offset.x(), offset.y(), offset.z());
            BlockData oldData = world.getBlockAt(loc).getBlockData();
            BlockData newData = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(clipboard.getFullBlock(vec));
            if (oldData.getMaterial() != newData.getMaterial()) {
                changes.add(new BlockChange(loc, oldData, newData));
            }
        }
        return changes;
    }

    private void playChanges(ParkourMap map, Region region, List<BlockChange> changes) {
        boolean particlesEnabled = map == null || map.isParticlesEnabled();
        boolean soundEnabled = map == null || map.isSoundEnabled();
        // Avoid sound spam: for each material involved, play break/place sounds only once per tick of schematic swap.
        Map<Material, BlockChange> firstBreakByMaterial = soundEnabled ? new HashMap<>() : null;
        Map<Material, BlockChange> firstPlaceByMaterial = soundEnabled ? new HashMap<>() : null;
        for (BlockChange change : changes) {
            Material oldMat = change.oldData.getMaterial();
            Material newMat = change.newData.getMaterial();
            if (!oldMat.isAir()) {
                if (soundEnabled) {
                    firstBreakByMaterial.putIfAbsent(oldMat, change);
                }
                if (particlesEnabled) {
                    change.location.getWorld().spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            change.location.clone().add(0.5, 0.5, 0.5),
                            10,
                            0.2,
                            0.2,
                            0.2,
                            0.0,
                            change.oldData
                    );
                }
            }
            if (!newMat.isAir()) {
                if (soundEnabled) {
                    firstPlaceByMaterial.putIfAbsent(newMat, change);
                }
                if (particlesEnabled) {
                    change.location.getWorld().spawnParticle(
                            Particle.BLOCK,
                            change.location.clone().add(0.5, 0.5, 0.5),
                            10,
                            0.2,
                            0.2,
                            0.2,
                            0.0,
                            change.newData
                    );
                }
            }
        }
        if (soundEnabled) {
            org.bukkit.World world = null;
            if (region != null && region.getWorldName() != null) {
                world = Bukkit.getWorld(region.getWorldName());
            }
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    if (region != null && !region.contains(player.getLocation())) {
                        continue;
                    }
                    Location at = player.getLocation();
                    for (BlockChange change : firstBreakByMaterial.values()) {
                        SoundGroup group = change.oldData.getSoundGroup();
                        if (group != null) {
                            player.playSound(at, group.getBreakSound(), 1.0f, 1.0f);
                        }
                    }
                    for (BlockChange change : firstPlaceByMaterial.values()) {
                        SoundGroup group = change.newData.getSoundGroup();
                        if (group != null) {
                            player.playSound(at, group.getPlaceSound(), 1.0f, 1.0f);
                        }
                    }
                }
            }
        }
    }

    private void tryDisableSideEffects(EditSession editSession) {
        try {
            Class<?> sideEffectSetClass = Class.forName("com.sk89q.worldedit.world.block.SideEffectSet");
            Method noneMethod = sideEffectSetClass.getMethod("none");
            Object noneSet = noneMethod.invoke(null);
            Method setSideEffect = EditSession.class.getMethod("setSideEffectApplier", sideEffectSetClass);
            setSideEffect.invoke(editSession, noneSet);
        } catch (Throwable ignored) {
            // Fallback to default behavior when side effect API is not available.
        }
    }

    private static class BlockChange {
        private final Location location;
        private final BlockData oldData;
        private final BlockData newData;

        private BlockChange(Location location, BlockData oldData, BlockData newData) {
            this.location = location;
            this.oldData = oldData;
            this.newData = newData;
        }
    }
}
