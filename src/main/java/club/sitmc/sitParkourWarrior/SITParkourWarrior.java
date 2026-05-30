package club.sitmc.sitParkourWarrior;

import club.sitmc.sitParkourWarrior.board.BoardData;
import club.sitmc.sitParkourWarrior.board.BoardManager;
import club.sitmc.sitParkourWarrior.board.BoardRenderer;
import club.sitmc.sitParkourWarrior.command.DPCommand;
import club.sitmc.sitParkourWarrior.config.CountdownScoringConfig;
import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.config.TimingMode;
import club.sitmc.sitParkourWarrior.course.CourseLayoutAnalyzer;
import club.sitmc.sitParkourWarrior.listener.ItemLockListener;
import club.sitmc.sitParkourWarrior.listener.PkwItemInteractListener;
import club.sitmc.sitParkourWarrior.listener.PlayerDeathListener;
import club.sitmc.sitParkourWarrior.listener.PlayerJoinListener;
import club.sitmc.sitParkourWarrior.listener.PlayerMoveListener;
import club.sitmc.sitParkourWarrior.listener.PlayerQuitListener;
import club.sitmc.sitParkourWarrior.listener.PlayerRespawnListener;
import club.sitmc.sitParkourWarrior.listener.SelectionToolListener;
import club.sitmc.sitParkourWarrior.listener.WorldChangeListener;
import club.sitmc.sitParkourWarrior.map.DynamicService;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.SelectionManager;
import club.sitmc.sitParkourWarrior.records.RecordsManager;
import club.sitmc.sitParkourWarrior.session.RunProgress;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.util.ItemUtil;
import club.sitmc.sitParkourWarrior.util.ParticleService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SITParkourWarrior extends JavaPlugin {

    private MapManager mapManager;
    private SessionManager sessionManager;
    private DynamicService dynamicService;
    private ParticleService particleService;
    private SelectionManager selectionManager;
    private CourseLayoutAnalyzer courseLayoutAnalyzer;
    private PkwWorldManager pkwWorldManager;
    private RecordsManager recordsManager;
    private CountdownScoringConfig countdownScoring;
    private BoardManager boardManager;
    private BukkitTask actionBarTask;
    private BukkitTask boardRefreshTask;

    @Override
    public void onEnable() {
        this.mapManager = new MapManager(this);
        this.dynamicService = new DynamicService(this, mapManager);
        this.particleService = new ParticleService(this);
        this.selectionManager = new SelectionManager(mapManager, particleService);
        ItemUtil.init(this);
        this.pkwWorldManager = new PkwWorldManager(getDataFolder());
        this.recordsManager = new RecordsManager(getDataFolder());
        this.countdownScoring = new CountdownScoringConfig(getDataFolder());
        this.boardManager = new BoardManager(this, getDataFolder());
        this.boardManager.init(new NamespacedKey(this, BoardManager.class.getSimpleName()));
        this.courseLayoutAnalyzer = new CourseLayoutAnalyzer(mapManager, pkwWorldManager, getDataFolder());
        this.sessionManager = new SessionManager(this, mapManager, dynamicService, recordsManager, pkwWorldManager, courseLayoutAnalyzer, countdownScoring);
        this.particleService.start(selectionManager);
        mapManager.loadAll();
        dynamicService.startAllDeployed();

        getServer().getPluginManager().registerEvents(new PlayerMoveListener(sessionManager, mapManager, selectionManager, pkwWorldManager, courseLayoutAnalyzer), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(sessionManager, pkwWorldManager, recordsManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(sessionManager, pkwWorldManager, recordsManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(sessionManager), this);
        getServer().getPluginManager().registerEvents(new SelectionToolListener(selectionManager), this);
        getServer().getPluginManager().registerEvents(new ItemLockListener(pkwWorldManager), this);
        getServer().getPluginManager().registerEvents(new WorldChangeListener(pkwWorldManager), this);
        getServer().getPluginManager().registerEvents(new PkwItemInteractListener(sessionManager, mapManager, pkwWorldManager, courseLayoutAnalyzer), this);

        DPCommand command = new DPCommand(mapManager, sessionManager, selectionManager, dynamicService, courseLayoutAnalyzer, pkwWorldManager, recordsManager, countdownScoring, boardManager);
        if (getCommand("sitpkw") != null) {
            getCommand("sitpkw").setExecutor(command);
            getCommand("sitpkw").setTabCompleter(command);
        } else {
            getLogger().warning("Command sitpkw not found in plugin.yml");
        }

        startActionBarTimer();
        boardManager.restoreAllEntities();
        startBoardRefreshTask();
    }

    private void startActionBarTimer() {
        actionBarTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (!pkwWorldManager.isPkwWorld(player.getWorld())) continue;
                RunProgress rp = sessionManager.getRunProgress(player.getUniqueId());
                if (rp == null) continue;

                long elapsed = rp.getElapsedMs();
                double sec = elapsed / 1000.0;
                String timeStr = formatTime(sec);

                TimingMode mode = pkwWorldManager.getTimingMode(player.getWorld());
                String msg;
                if (mode == TimingMode.COUNTDOWN) {
                    long remaining = sessionManager.getCountdownRemainingMs(player.getUniqueId(), player.getWorld().getName());
                    double remSec = Math.max(0, remaining) / 1000.0;
                    msg = "§e倒计时: §f" + formatTime(remSec) + " §e奖牌: §f石" + rp.getStoneCount()
                            + " 铜" + rp.getBronzeCount() + " 银" + rp.getSilverCount() + " 金" + rp.getGoldCount();
                } else {
                    msg = "§e全程用时: §f" + timeStr;
                }
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
            }
        }, 0L, 1L);
    }

    private static String formatTime(double totalSec) {
        int min = (int) (totalSec / 60);
        double sec = totalSec % 60;
        if (min > 0) {
            return min + ":" + String.format("%05.2f", sec);
        }
        return String.format("%.2f", sec);
    }

    private void startBoardRefreshTask() {
        boardManager.setOnBoardChanged(() -> refreshAllBoards());
        boardRefreshTask = Bukkit.getScheduler().runTaskTimer(this, this::scanAndRefreshBoards, 0L, 10L);
    }

    private boolean boardRefreshNeeded = true;
    private final java.util.Map<String, String> lastNearestPerBoard = new java.util.HashMap<>(); // board key -> player name

    private void refreshAllBoards() {
        boardRefreshNeeded = true;
    }

    private void scanAndRefreshBoards() {
        for (BoardData board : boardManager.getBoards()) {
            var display = boardManager.getEntity(board);
            if (display == null) continue;

            String playersInRangeKey = board.getWorldName() + ":" + board.getTier() + ":" + board.getEntityUuid();
            String nearestName = null;
            String nearestTime = null;
            int nearestRank = -1;
            boolean nearestInTop10 = false;

            // Find nearest player within 10 blocks
            org.bukkit.World world = Bukkit.getWorld(board.getWorldName());
            if (world != null) {
                double bestDist = Double.MAX_VALUE;
                org.bukkit.entity.Player nearest = null;
                for (org.bukkit.entity.Player p : world.getPlayers()) {
                    if (!board.isInRange(p.getLocation())) continue;
                    double d = board.distanceSquared(p.getLocation());
                    if (d < bestDist) { bestDist = d; nearest = p; }
                }
                if (nearest != null) {
                    nearestName = nearest.getName();
                    boolean isCountdown = "countdown".equals(board.getTier());
                    if (isCountdown) {
                        BoardRenderer.PlayerRankInfo info = BoardRenderer.findPlayerRankCountdown(
                                board.getWorldName(), nearestName, recordsManager);
                        if (info != null) {
                            nearestRank = info.rank;
                            nearestTime = info.display;
                            nearestInTop10 = info.rank <= 10;
                        }
                    } else {
                        BoardRenderer.PlayerRankInfo info = BoardRenderer.findPlayerRankCountup(
                                board.getWorldName(), board.getTier(), nearestName, recordsManager);
                        if (info != null) {
                            nearestRank = info.rank;
                            nearestTime = info.display;
                            nearestInTop10 = info.rank <= 10;
                        }
                    }
                }
            }

            String prevNearest = lastNearestPerBoard.get(playersInRangeKey);
            boolean nearestChanged = !String.valueOf(nearestName).equals(String.valueOf(prevNearest));

            if (boardRefreshNeeded || nearestChanged) {
                String text = BoardRenderer.build(board, recordsManager,
                        nearestName, nearestRank, nearestTime, nearestInTop10);
                display.setText(text);
                lastNearestPerBoard.put(playersInRangeKey, nearestName);
            }
        }
        boardRefreshNeeded = false;
    }

    @Override
    public void onDisable() {
        if (boardRefreshTask != null) {
            boardRefreshTask.cancel();
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        if (dynamicService != null) {
            dynamicService.stopAll();
        }
        if (particleService != null) {
            particleService.stopAll();
        }
        if (mapManager != null) {
            mapManager.saveAll();
        }
    }
}
