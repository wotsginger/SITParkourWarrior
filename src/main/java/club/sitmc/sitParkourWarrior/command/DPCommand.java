package club.sitmc.sitParkourWarrior.command;

import club.sitmc.sitParkourWarrior.config.PkwWorldManager;
import club.sitmc.sitParkourWarrior.course.CourseLayoutAnalyzer;
import club.sitmc.sitParkourWarrior.map.Deployment;
import club.sitmc.sitParkourWarrior.map.Difficulty;
import club.sitmc.sitParkourWarrior.map.DynamicData;
import club.sitmc.sitParkourWarrior.map.DynamicService;
import club.sitmc.sitParkourWarrior.map.EndTier;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.NodeType;
import club.sitmc.sitParkourWarrior.map.ParkourMap;
import club.sitmc.sitParkourWarrior.map.PointLocation;
import club.sitmc.sitParkourWarrior.map.Region;
import club.sitmc.sitParkourWarrior.map.SelectionManager;
import club.sitmc.sitParkourWarrior.map.SchematicService;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DPCommand implements CommandExecutor, TabCompleter {

    private final MapManager mapManager;
    private final SessionManager sessionManager;
    private final SelectionManager selectionManager;
    private final DynamicService dynamicService;
    private final CourseLayoutAnalyzer courseLayoutAnalyzer;
    private final PkwWorldManager pkwWorldManager;
    private final SchematicService schematicService = new SchematicService();

    public DPCommand(MapManager mapManager, SessionManager sessionManager, SelectionManager selectionManager, DynamicService dynamicService, CourseLayoutAnalyzer courseLayoutAnalyzer, PkwWorldManager pkwWorldManager) {
        this.mapManager = mapManager;
        this.sessionManager = sessionManager;
        this.selectionManager = selectionManager;
        this.dynamicService = dynamicService;
        this.courseLayoutAnalyzer = courseLayoutAnalyzer;
        this.pkwWorldManager = pkwWorldManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                return handleCreate(sender, args);
            case "edit":
                return handleEdit(sender, args);
            case "exit":
                return handleExit(sender);
            case "particles":
                return handleParticles(sender, args);
            case "sound":
                return handleSound(sender, args);
            case "pos1":
                return handlePos(sender, true);
            case "pos2":
                return handlePos(sender, false);
            case "setstart":
                return handleSetStart(sender, args);
            case "setend":
                return handleSetEnd(sender, args);
            case "title":
                return handleTitle(sender, args);
            case "difficulty":
                return handleDifficulty(sender, args);
            case "dynamic":
                return handleDynamic(sender, args);
            case "deploy":
                return handleDeploy(sender, args);
            case "undeploy":
                return handleUndeploy(sender, args);
            case "save":
                return handleSave(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "addforkpoint":
                return handleAddForkPoint(sender);
            case "delforkpoint":
                return handleDelForkPoint(sender, args);
            case "setendtier":
                return handleSetEndTier(sender, args);
            case "reload":
                return handleReload(sender);
            case "pkwworld":
                return handlePkwWorld(sender, args);
            default:
                Msg.send(sender, "未知子命令。输入 /sitpkw 查看用法。");
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        Msg.send(sender, "用法:");
        Msg.send(sender, "/sitpkw create <id> [type]  创建关卡并进入编辑,type:level/fork/globalstart/globalend/branchend,缺省level");
        Msg.send(sender, "/sitpkw edit <id>    进入编辑模式");
        Msg.send(sender, "/sitpkw exit         退出编辑模式");
        Msg.send(sender, "/sitpkw particles <on|off>  设置关卡粒子效果");
        Msg.send(sender, "/sitpkw sound <on|off>      设置关卡破坏音效");
        Msg.send(sender, "/sitpkw pos1|pos2    设置选区角点");
        Msg.send(sender, "/sitpkw save <序列id> 保存当前选区为动态状态");
        Msg.send(sender, "/sitpkw setstart [pos1|pos2]  设置起点（或起点区域的角点）");
        Msg.send(sender, "/sitpkw setend [pos1|pos2]    设置终点（或终点区域的角点）");
        Msg.send(sender, "/sitpkw title <文本>  设置标题");
        Msg.send(sender, "/sitpkw difficulty <easy|normal|hard|extreme> 设置难度");
        Msg.send(sender, "/sitpkw dynamic addstate <文件名> [间隔]  添加动态状态");
        Msg.send(sender, "/sitpkw dynamic delstate <序号|文件名>     删除动态状态");
        Msg.send(sender, "/sitpkw dynamic interval <序号> <时间>    修改状态间隔（tick）");
        Msg.send(sender, "/sitpkw dynamic list                   查看动态列表");
        Msg.send(sender, "/sitpkw deploy [id]                    在当前位置放出关卡");
        Msg.send(sender, "/sitpkw undeploy                       在当前位置收回关卡");
        Msg.send(sender, "/sitpkw addforkpoint                   为当前编辑的FORK节点添加分支点");
        Msg.send(sender, "/sitpkw delforkpoint <序号>            删除当前编辑FORK节点的分支点");
        Msg.send(sender, "/sitpkw setendtier <easy|normal|hard>  设置全局终点难度类型");
        Msg.send(sender, "/sitpkw delete <id>                    删除关卡");
        Msg.send(sender, "/sitpkw pkwworld add [世界名]          加入 PKW 世界列表（省略则为当前世界）");
        Msg.send(sender, "/sitpkw pkwworld remove [世界名]       移出 PKW 世界列表（省略则为当前世界）");
        Msg.send(sender, "/sitpkw pkwworld list                  列出所有 PKW 世界");
        Msg.send(sender, "/sitpkw reload                         重载配置");
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw create <id> [type]");
            return true;
        }
        String id = args[1];
        NodeType nodeType = NodeType.LEVEL;
        if (args.length >= 3) {
            nodeType = NodeType.fromString(args[2]);
        }
        ParkourMap map = mapManager.createMap(id);
        if (map == null) {
            Msg.send(sender, "该 id 已存在。");
            return true;
        }
        map.setNodeType(nodeType);
        selectionManager.setEditingMap(player, map);
        mapManager.saveMap(map);
        Msg.send(sender, "已创建关卡（" + nodeType.toConfigString() + "）并进入编辑: " + map.getId());
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw edit <id>");
            return true;
        }
        ParkourMap map = mapManager.getMap(args[1]);
        if (map == null) {
            Msg.send(sender, "关卡不存在。");
            return true;
        }
        selectionManager.setEditingMap(player, map);
        Msg.send(sender, "已进入编辑: " + map.getId());
        return true;
    }

    private boolean handleExit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        if (!selectionManager.isEditing(player)) {
            Msg.send(sender, "当前不在编辑模式。");
            return true;
        }
        selectionManager.clearEditingMap(player);
        Msg.send(sender, "已退出编辑模式。");
        return true;
    }

    private boolean handleParticles(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw particles <on|off>");
            return true;
        }
        String value = args[1].toLowerCase();
        boolean enabled;
        if (value.equals("on")) {
            enabled = true;
        } else if (value.equals("off")) {
            enabled = false;
        } else {
            Msg.send(sender, "用法: /sitpkw particles <on|off>");
            return true;
        }
        map.setParticlesEnabled(enabled);
        mapManager.saveMap(map);
        Msg.send(sender, enabled ? "已开启关卡粒子效果。" : "已关闭关卡粒子效果。");
        return true;
    }

    private boolean handleSound(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw sound <on|off>");
            return true;
        }
        String value = args[1].toLowerCase();
        boolean enabled;
        if (value.equals("on")) {
            enabled = true;
        } else if (value.equals("off")) {
            enabled = false;
        } else {
            Msg.send(sender, "用法: /sitpkw sound <on|off>");
            return true;
        }
        map.setSoundEnabled(enabled);
        mapManager.saveMap(map);
        Msg.send(sender, enabled ? "已开启关卡破坏音效。" : "已关闭关卡破坏音效。");
        return true;
    }

    private boolean handlePos(CommandSender sender, boolean first) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        selectionManager.setPos(player, first);
        return true;
    }

    private boolean handleSetStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        String zone = args.length >= 2 ? args[1].toLowerCase() : "";
        switch (zone) {
            case "pos1":
                map.setStartPos1(PointLocation.fromLocation(player.getLocation()));
                mapManager.saveMap(map);
                Msg.send(sender, "起点区域角点1已设置。");
                return true;
            case "pos2":
                map.setStartPos2(PointLocation.fromLocation(player.getLocation()));
                mapManager.saveMap(map);
                Msg.send(sender, "起点区域角点2已设置。");
                return true;
            default:
                map.setStart(player.getLocation());
                mapManager.saveMap(map);
                Msg.send(sender, "起点已设置" + (args.length >= 2 ? "（未知参数 \"" + args[1] + "\"，已设为起点中心点）" : "。"));
                return true;
        }
    }

    private boolean handleSetEnd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        String zone = args.length >= 2 ? args[1].toLowerCase() : "";
        switch (zone) {
            case "pos1":
                map.setEndPos1(PointLocation.fromLocation(player.getLocation()));
                mapManager.saveMap(map);
                Msg.send(sender, "终点区域角点1已设置。");
                return true;
            case "pos2":
                map.setEndPos2(PointLocation.fromLocation(player.getLocation()));
                mapManager.saveMap(map);
                Msg.send(sender, "终点区域角点2已设置。");
                return true;
            default:
                map.setEnd(player.getLocation());
                mapManager.saveMap(map);
                Msg.send(sender, "终点已设置" + (args.length >= 2 ? "（未知参数 \"" + args[1] + "\"，已设为终点中心点）" : "。"));
                return true;
        }
    }

    private boolean handleTitle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw title <文本>");
            return true;
        }
        String title = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        map.setTitle(title);
        mapManager.saveMap(map);
        Msg.send(sender, "标题已设置。");
        return true;
    }

    private boolean handleDifficulty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw difficulty <easy|normal|hard|extreme>");
            return true;
        }
        Difficulty difficulty = Difficulty.fromString(args[1]);
        map.setDifficulty(difficulty);
        mapManager.saveMap(map);
        Msg.send(sender, "难度已设置为 " + difficulty.name().toLowerCase() + "。");
        return true;
    }

    private boolean handleDynamic(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw dynamic <addstate|delstate|interval|list>");
            return true;
        }
        String action = args[1].toLowerCase();
        DynamicData data = map.getDynamicData();
        switch (action) {
            case "addstate":
                if (args.length < 3) {
                    Msg.send(sender, "用法: /sitpkw dynamic addstate <文件名> [间隔]");
                    return true;
                }
                data.getStates().add(args[2]);
                int interval = 1;
                if (args.length >= 4) {
                    try {
                        interval = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ex) {
                        Msg.send(sender, "无效间隔: " + args[3]);
                        return true;
                    }
                }
                if (interval <= 0) {
                    interval = 1;
                }
                data.getIntervalSequence().add(interval);
                mapManager.saveMap(map);
                Msg.send(sender, "已添加状态 " + args[2] + "，间隔 " + interval + "。");
                return true;
            case "delstate":
                if (args.length < 3) {
                    Msg.send(sender, "用法: /sitpkw dynamic delstate <序号|文件名>");
                    return true;
                }
                int index = parseStateIndex(data, args[2]);
                if (index < 0 || index >= data.getStates().size()) {
                    Msg.send(sender, "找不到对应的状态。");
                    return true;
                }
                String removed = data.getStates().remove(index);
                if (index < data.getIntervalSequence().size()) {
                    data.getIntervalSequence().remove(index);
                }
                mapManager.saveMap(map);
                Msg.send(sender, "已删除状态 " + removed + "。");
                return true;
            case "interval":
                if (args.length < 4) {
                    Msg.send(sender, "用法: /sitpkw dynamic interval <序号> <时间>");
                    return true;
                }
                int targetIndex;
                try {
                    targetIndex = Integer.parseInt(args[2]) - 1;
                } catch (NumberFormatException ex) {
                    Msg.send(sender, "无效序号: " + args[2]);
                    return true;
                }
                if (targetIndex < 0 || targetIndex >= data.getStates().size()) {
                    Msg.send(sender, "找不到对应的状态。");
                    return true;
                }
                int ticks;
                try {
                    ticks = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    Msg.send(sender, "无效时间: " + args[3]);
                    return true;
                }
                if (ticks <= 0) {
                    ticks = 1;
                }
                while (data.getIntervalSequence().size() < data.getStates().size()) {
                    data.getIntervalSequence().add(1);
                }
                data.getIntervalSequence().set(targetIndex, ticks);
                mapManager.saveMap(map);
                Msg.send(sender, "已更新状态间隔为 " + ticks + "。");
                return true;
            case "list":
                if (data.getStates().isEmpty()) {
                    Msg.send(sender, "当前没有动态状态。");
                    return true;
                }
                while (data.getIntervalSequence().size() < data.getStates().size()) {
                    data.getIntervalSequence().add(1);
                }
                while (data.getIntervalSequence().size() > data.getStates().size()) {
                    data.getIntervalSequence().remove(data.getIntervalSequence().size() - 1);
                }
                Msg.send(sender, "动态状态列表（序号: 文件名 / 间隔）");
                for (int i = 0; i < data.getStates().size(); i++) {
                    Msg.send(sender, (i + 1) + ": " + data.getStates().get(i) + " / " + data.getIntervalSequence().get(i));
                }
                return true;
            default:
                Msg.send(sender, "未知 dynamic 子命令。");
                return true;
        }
    }

    private int parseStateIndex(DynamicData data, String token) {
        if (data == null || token == null) {
            return -1;
        }
        try {
            int oneBased = Integer.parseInt(token);
            return oneBased - 1;
        } catch (NumberFormatException ex) {
            // ignore
        }
        for (int i = 0; i < data.getStates().size(); i++) {
            if (data.getStates().get(i).equalsIgnoreCase(token)) {
                return i;
            }
        }
        return -1;
    }

    private boolean handleDeploy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = null;
        if (args.length >= 2) {
            map = mapManager.getMap(args[1]);
        } else {
            map = selectionManager.getEditingMap(player);
        }
        if (map == null) {
            Msg.send(sender, "关卡不存在，请先进入编辑或提供 id。");
            return true;
        }

        NodeType type = map.getNodeType();

        // ---- type-specific validation ----
        if (map.getRegion() == null) {
            Msg.send(sender, "关卡未设置区域。");
            return true;
        }
        switch (type) {
            case LEVEL:
                if (map.getStart() == null || map.getEnd() == null) {
                    Msg.send(sender, "关卡未设置起点或终点。");
                    return true;
                }
                break;
            case FORK:
                if (map.getForkBranchPoints().isEmpty()) {
                    Msg.send(sender, "岔路口节点需要先设置区域和至少一个岔路点位。");
                    return true;
                }
                break;
            case GLOBAL_START:
                if (map.getStart() == null) {
                    Msg.send(sender, "全局起点未设置起点。");
                    return true;
                }
                for (ParkourMap m : mapManager.getMaps().values()) {
                    if (m == map) continue;
                    if (m.getNodeType() == NodeType.GLOBAL_START && !m.getDeployments().isEmpty()) {
                        Msg.send(sender, "全局起点已存在，无法部署第二个，请先回收已有的全局起点。");
                        return true;
                    }
                }
                break;
            case GLOBAL_END:
            case BRANCH_END:
                if (map.getEnd() == null) {
                    Msg.send(sender, "终点节点未设置终点。");
                    return true;
                }
                break;
            default:
                if (map.getStart() == null || map.getEnd() == null) {
                    Msg.send(sender, "关卡未设置起点或终点。");
                    return true;
                }
                break;
        }

        Location origin = player.getLocation().getBlock().getLocation();
        Region template = map.getRegion();
        int sizeX = template.getMaxX() - template.getMinX();
        int sizeY = template.getMaxY() - template.getMinY();
        int sizeZ = template.getMaxZ() - template.getMinZ();

        // ---- offset reference point by type ----
        PointLocation refPoint;
        switch (type) {
            case FORK:
                refPoint = map.getForkBranchPoints().get(0);
                break;
            case GLOBAL_END:
            case BRANCH_END:
                refPoint = map.getEnd();
                break;
            default:
                refPoint = map.getStart();
                break;
        }

        int offsetX = origin.getBlockX() - refPoint.getBlockX();
        int offsetY = origin.getBlockY() - refPoint.getBlockY();
        int offsetZ = origin.getBlockZ() - refPoint.getBlockZ();
        int newMinX = template.getMinX() + offsetX;
        int newMinY = template.getMinY() + offsetY;
        int newMinZ = template.getMinZ() + offsetZ;
        int newMaxX = newMinX + sizeX;
        int newMaxY = newMinY + sizeY;
        int newMaxZ = newMinZ + sizeZ;

        Region newRegion = new Region(origin.getWorld().getName(), newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ);
        String worldName = origin.getWorld().getName();

        // ---- translate coordinates by type ----
        PointLocation newStart = null;
        PointLocation newEnd = null;
        List<PointLocation> newForkPoints = new ArrayList<>();

        switch (type) {
            case LEVEL:
                newStart = translatePoint(map.getStart(), worldName, offsetX, offsetY, offsetZ);
                newEnd = translatePoint(map.getEnd(), worldName, offsetX, offsetY, offsetZ);
                break;
            case FORK:
                for (PointLocation fp : map.getForkBranchPoints()) {
                    newForkPoints.add(translatePoint(fp, worldName, offsetX, offsetY, offsetZ));
                }
                break;
            case GLOBAL_START:
                newStart = translatePoint(map.getStart(), worldName, offsetX, offsetY, offsetZ);
                break;
            case GLOBAL_END:
            case BRANCH_END:
                newEnd = translatePoint(map.getEnd(), worldName, offsetX, offsetY, offsetZ);
                break;
            default:
                newStart = translatePoint(map.getStart(), worldName, offsetX, offsetY, offsetZ);
                newEnd = translatePoint(map.getEnd(), worldName, offsetX, offsetY, offsetZ);
                break;
        }

        // Translate optional custom zone corners (null-safe).
        PointLocation newStartPos1 = translatePointOrNull(map.getStartPos1(), worldName, offsetX, offsetY, offsetZ);
        PointLocation newStartPos2 = translatePointOrNull(map.getStartPos2(), worldName, offsetX, offsetY, offsetZ);
        PointLocation newEndPos1   = translatePointOrNull(map.getEndPos1(),   worldName, offsetX, offsetY, offsetZ);
        PointLocation newEndPos2   = translatePointOrNull(map.getEndPos2(),   worldName, offsetX, offsetY, offsetZ);

        // ---- emerald marker: only for types that have an end ----
        if (newEnd != null) {
            Location endLoc = newEnd.toLocation();
            if (endLoc != null && endLoc.getWorld() != null) {
                endLoc.clone().subtract(0, 1, 0).getBlock().setType(Material.EMERALD_BLOCK);
            }
        }

        Deployment deployment = new Deployment(UUID.randomUUID().toString(), newRegion, newStart, newEnd,
                newForkPoints, newStartPos1, newStartPos2, newEndPos1, newEndPos2);
        map.addDeployment(deployment);

        // Schematic paste (region-based, works for all types).
        File mapFolder = mapManager.getMapFolder(map);
        String initialState = map.getDynamicData().getStates().isEmpty() ? null : map.getDynamicData().getStates().get(0);
        if (initialState != null) {
            File schemFile = new File(mapFolder, initialState);
            if (schemFile.exists()) {
                schematicService.pasteSchematic(schemFile, new Location(origin.getWorld(), newMinX, newMinY, newMinZ));
            }
        }
        dynamicService.startForDeployment(map, deployment);

        mapManager.saveMap(map);
        if (pkwWorldManager.isPkwWorld(worldName)) {
            courseLayoutAnalyzer.recomputeWorld(worldName);
        }
        Msg.send(sender, "已放出关卡: " + map.getId());
        return true;
    }

    private PointLocation translatePoint(PointLocation template, String worldName, int offsetX, int offsetY, int offsetZ) {
        return new PointLocation(worldName,
                template.getX() + offsetX,
                template.getY() + offsetY,
                template.getZ() + offsetZ,
                template.getYaw(),
                template.getPitch());
    }

    private PointLocation translatePointOrNull(PointLocation template, String worldName, int offsetX, int offsetY, int offsetZ) {
        if (template == null) return null;
        return translatePoint(template, worldName, offsetX, offsetY, offsetZ);
    }

    private boolean handleUndeploy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = null;
        if (args.length >= 2) {
            map = mapManager.getMap(args[1]);
        }
        Deployment deployment = null;
        if (map != null) {
            deployment = map.findDeploymentByLocation(player.getLocation());
        } else {
            for (ParkourMap m : mapManager.getMaps().values()) {
                Deployment found = m.findDeploymentByLocation(player.getLocation());
                if (found != null) {
                    map = m;
                    deployment = found;
                    break;
                }
            }
        }
        if (map == null || deployment == null) {
            Msg.send(sender, "当前位置不在任何关卡的放置区域内。");
            return true;
        }
        sessionManager.endSessionsForDeployment(map.getId(), deployment.getId());
        dynamicService.stopForDeployment(map, deployment);
        schematicService.clearRegion(deployment.getRegion());
        map.removeDeployment(deployment.getId());
        mapManager.saveMap(map);
        String depWorld = deployment.getRegion().getWorldName();
        if (pkwWorldManager.isPkwWorld(depWorld)) {
            courseLayoutAnalyzer.recomputeWorld(depWorld);
        }
        Msg.send(sender, "已收回关卡: " + map.getId());
        return true;
    }

    private boolean handleSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw save <序列id>");
            return true;
        }
        int seqId;
        try {
            seqId = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            Msg.send(sender, "序列 id 必须是数字。");
            return true;
        }
        selectionManager.saveSelection(player, seqId);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw delete <id>");
            return true;
        }
        ParkourMap map = mapManager.getMap(args[1]);
        if (map == null) {
            Msg.send(sender, "关卡不存在。");
            return true;
        }
        sessionManager.endSessionsForMap(map.getId());
        mapManager.deleteMap(map.getId());
        Msg.send(sender, "已删除关卡: " + map.getId());
        return true;
    }

    private boolean handleAddForkPoint(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        if (map.getNodeType() != NodeType.FORK) {
            Msg.send(sender, "只有 FORK 节点可以添加分支点。");
            return true;
        }
        Location loc = player.getLocation();
        map.getForkBranchPoints().add(PointLocation.fromLocation(loc));
        mapManager.saveMap(map);
        Msg.send(sender, "已添加分支点 #" + map.getForkBranchPoints().size() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
        return true;
    }

    private boolean handleDelForkPoint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        if (map.getNodeType() != NodeType.FORK) {
            Msg.send(sender, "只有 FORK 节点可以删除分支点。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw delforkpoint <序号>");
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            Msg.send(sender, "序号必须是数字。");
            return true;
        }
        if (index < 1 || index > map.getForkBranchPoints().size()) {
            Msg.send(sender, "序号超出范围（1-" + map.getForkBranchPoints().size() + "）。");
            return true;
        }
        PointLocation removed = map.getForkBranchPoints().remove(index - 1);
        mapManager.saveMap(map);
        Msg.send(sender, "已删除分支点 #" + index + " (" + removed.getBlockX() + ", " + removed.getBlockY() + ", " + removed.getBlockZ() + ")");
        return true;
    }

    private boolean handleSetEndTier(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        if (map.getNodeType() != NodeType.GLOBAL_END) {
            Msg.send(sender, "该命令仅适用于全局终点(globalend)类型节点。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw setendtier <easy|normal|hard>");
            return true;
        }
        EndTier tier = EndTier.fromString(args[1]);
        map.setEndTier(tier);
        mapManager.saveMap(map);
        Msg.send(sender, "全局终点难度类型已设置为 " + tier.toConfigString() + "。");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        sessionManager.endAll();
        dynamicService.stopAll();
        mapManager.loadAll();
        dynamicService.startAllDeployed();
        courseLayoutAnalyzer.recomputeAllPkwWorlds();
        Msg.send(sender, "已重载关卡配置。");
        return true;
    }

    private boolean handlePkwWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw pkwworld <add|remove|list> [世界名]");
            return true;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "add": {
                String worldName;
                if (args.length >= 3) {
                    worldName = args[2];
                } else if (sender instanceof Player player) {
                    worldName = player.getWorld().getName();
                } else {
                    Msg.send(sender, "控制台必须指定世界名: /sitpkw pkwworld add <世界名>");
                    return true;
                }
                if (Bukkit.getWorld(worldName) == null) {
                    Msg.send(sender, "世界 " + worldName + " 不存在或未加载。");
                    return true;
                }
                boolean added = pkwWorldManager.add(worldName);
                if (added) {
                    courseLayoutAnalyzer.recomputeWorld(worldName);
                }
                Msg.send(sender, added ? "已将 " + worldName + " 加入 PKW 世界列表。" : worldName + " 已在 PKW 世界列表中。");
                return true;
            }
            case "remove": {
                String worldName;
                if (args.length >= 3) {
                    worldName = args[2];
                } else if (sender instanceof Player player) {
                    worldName = player.getWorld().getName();
                } else {
                    Msg.send(sender, "控制台必须指定世界名: /sitpkw pkwworld remove <世界名>");
                    return true;
                }
                boolean removed = pkwWorldManager.remove(worldName);
                if (removed) {
                    courseLayoutAnalyzer.removeWorld(worldName);
                }
                Msg.send(sender, removed ? "已将 " + worldName + " 移出 PKW 世界列表。" : worldName + " 不在 PKW 世界列表中。");
                return true;
            }
            case "list": {
                java.util.Set<String> worlds = pkwWorldManager.getWorlds();
                if (worlds.isEmpty()) {
                    Msg.send(sender, "当前没有 PKW 世界。");
                } else {
                    Msg.send(sender, "PKW 世界列表:");
                    for (String w : worlds) {
                        Msg.send(sender, "  - " + w);
                    }
                }
                return true;
            }
            default:
                Msg.send(sender, "用法: /sitpkw pkwworld <add|remove|list> [世界名]");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("create", "edit", "exit", "particles", "sound", "pos1", "pos2", "setstart", "setend", "title", "difficulty", "dynamic", "deploy", "undeploy", "addforkpoint", "delforkpoint", "setendtier", "save", "delete", "pkwworld", "reload"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return filter(args[2], Arrays.asList("level", "fork", "globalstart", "globalend", "branchend"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setendtier")) {
            return filter(args[1], Arrays.asList("easy", "normal", "hard"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("difficulty")) {
            return filter(args[1], Arrays.asList("easy", "normal", "hard", "extreme"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("dynamic")) {
            return filter(args[1], Arrays.asList("addstate", "delstate", "interval", "list"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("setstart") || args[0].equalsIgnoreCase("setend"))) {
            return filter(args[1], Arrays.asList("pos1", "pos2"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("particles") || args[0].equalsIgnoreCase("sound"))) {
            return filter(args[1], Arrays.asList("on", "off"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("deploy")
                || args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("delete"))) {
            return filter(args[1], new ArrayList<>(mapManager.getMaps().keySet()));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pkwworld")) {
            return filter(args[1], Arrays.asList("add", "remove", "list"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pkwworld")
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            List<String> worldNames = new ArrayList<>();
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                worldNames.add(w.getName());
            }
            return filter(args[2], worldNames);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delforkpoint") && sender instanceof Player player) {
            ParkourMap map = selectionManager.getEditingMap(player);
            if (map != null) {
                List<String> indices = new ArrayList<>();
                for (int i = 1; i <= map.getForkBranchPoints().size(); i++) {
                    indices.add(String.valueOf(i));
                }
                return filter(args[1], indices);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(String token, List<String> options) {
        if (token == null || token.isBlank()) {
            return options;
        }
        String lower = token.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}


