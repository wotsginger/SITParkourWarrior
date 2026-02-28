package club.sitmc.sitParkourWarrior.command;

import club.sitmc.sitParkourWarrior.map.Deployment;
import club.sitmc.sitParkourWarrior.map.Difficulty;
import club.sitmc.sitParkourWarrior.map.DynamicData;
import club.sitmc.sitParkourWarrior.map.DynamicService;
import club.sitmc.sitParkourWarrior.map.MapManager;
import club.sitmc.sitParkourWarrior.map.ParkourMap;
import club.sitmc.sitParkourWarrior.map.Region;
import club.sitmc.sitParkourWarrior.map.SelectionManager;
import club.sitmc.sitParkourWarrior.map.SchematicService;
import club.sitmc.sitParkourWarrior.session.SessionManager;
import club.sitmc.sitParkourWarrior.util.Msg;
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
    private final SchematicService schematicService = new SchematicService();

    public DPCommand(MapManager mapManager, SessionManager sessionManager, SelectionManager selectionManager, DynamicService dynamicService) {
        this.mapManager = mapManager;
        this.sessionManager = sessionManager;
        this.selectionManager = selectionManager;
        this.dynamicService = dynamicService;
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
                return handleSetStart(sender);
            case "setend":
                return handleSetEnd(sender);
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
            case "reload":
                return handleReload(sender);
            default:
                Msg.send(sender, "未知子命令。输入 /sitpkw 查看用法。");
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        Msg.send(sender, "用法:");
        Msg.send(sender, "/sitpkw create <id>  创建关卡并进入编辑");
        Msg.send(sender, "/sitpkw edit <id>    进入编辑模式");
        Msg.send(sender, "/sitpkw exit         退出编辑模式");
        Msg.send(sender, "/sitpkw particles <on|off>  设置关卡粒子效果");
        Msg.send(sender, "/sitpkw sound <on|off>      设置关卡破坏音效");
        Msg.send(sender, "/sitpkw pos1|pos2    设置选区角点");
        Msg.send(sender, "/sitpkw save <序列id> 保存当前选区为动态状态");
        Msg.send(sender, "/sitpkw setstart     设置起点");
        Msg.send(sender, "/sitpkw setend       设置终点");
        Msg.send(sender, "/sitpkw title <文本>  设置标题");
        Msg.send(sender, "/sitpkw difficulty <easy|normal|hard|extreme> 设置难度");
        Msg.send(sender, "/sitpkw dynamic addstate <文件名> [间隔]  添加动态状态");
        Msg.send(sender, "/sitpkw dynamic delstate <序号|文件名>     删除动态状态");
        Msg.send(sender, "/sitpkw dynamic interval <序号> <时间>    修改状态间隔（tick）");
        Msg.send(sender, "/sitpkw dynamic list                   查看动态列表");
        Msg.send(sender, "/sitpkw deploy [id]                    在当前位置放出关卡");
        Msg.send(sender, "/sitpkw undeploy                       在当前位置收回关卡");
        Msg.send(sender, "/sitpkw delete <id>                    删除关卡");
        Msg.send(sender, "/sitpkw reload                         重载配置");
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        if (args.length < 2) {
            Msg.send(sender, "用法: /sitpkw create <id>");
            return true;
        }
        String id = args[1];
        ParkourMap map = mapManager.createMap(id);
        if (map == null) {
            Msg.send(sender, "该 id 已存在。");
            return true;
        }
        selectionManager.setEditingMap(player, map);
        mapManager.saveMap(map);
        Msg.send(sender, "已创建关卡并进入编辑: " + map.getId());
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

    private boolean handleSetStart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        map.setStart(player.getLocation());
        mapManager.saveMap(map);
        Msg.send(sender, "起点已设置。");
        return true;
    }

    private boolean handleSetEnd(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "只能由玩家执行。");
            return true;
        }
        ParkourMap map = selectionManager.getEditingMap(player);
        if (map == null) {
            Msg.send(sender, "请先 /sitpkw create <id> 或 /sitpkw edit <id>。");
            return true;
        }
        map.setEnd(player.getLocation());
        mapManager.saveMap(map);
        Msg.send(sender, "终点已设置。");
        return true;
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
        if (map.getRegion() == null || map.getStart() == null || map.getEnd() == null) {
            Msg.send(sender, "关卡未设置区域或起点/终点。");
            return true;
        }
        Location origin = player.getLocation().getBlock().getLocation();
        Region template = map.getRegion();
        int sizeX = template.getMaxX() - template.getMinX();
        int sizeY = template.getMaxY() - template.getMinY();
        int sizeZ = template.getMaxZ() - template.getMinZ();
        Location templateStart = map.getStart();
        int offsetX = origin.getBlockX() - templateStart.getBlockX();
        int offsetY = origin.getBlockY() - templateStart.getBlockY();
        int offsetZ = origin.getBlockZ() - templateStart.getBlockZ();
        int newMinX = template.getMinX() + offsetX;
        int newMinY = template.getMinY() + offsetY;
        int newMinZ = template.getMinZ() + offsetZ;
        int newMaxX = newMinX + sizeX;
        int newMaxY = newMinY + sizeY;
        int newMaxZ = newMinZ + sizeZ;

        Location newStart = new Location(origin.getWorld(),
                map.getStart().getX() + offsetX,
                map.getStart().getY() + offsetY,
                map.getStart().getZ() + offsetZ,
                map.getStart().getYaw(),
                map.getStart().getPitch());
        Location newEnd = new Location(origin.getWorld(),
                map.getEnd().getX() + offsetX,
                map.getEnd().getY() + offsetY,
                map.getEnd().getZ() + offsetZ,
                map.getEnd().getYaw(),
                map.getEnd().getPitch());

        if (newEnd.getWorld() != null) {
            newEnd.clone().subtract(0, 1, 0).getBlock().setType(Material.EMERALD_BLOCK);
        }

        Deployment deployment = new Deployment(UUID.randomUUID().toString(),
                new Region(origin.getWorld().getName(), newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ),
                newStart,
                newEnd);
        map.addDeployment(deployment);

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
        Msg.send(sender, "已放出关卡: " + map.getId());
        return true;
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

    private boolean handleReload(CommandSender sender) {
        sessionManager.endAll();
        dynamicService.stopAll();
        mapManager.loadAll();
        dynamicService.startAllDeployed();
        Msg.send(sender, "已重载关卡配置。");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("create", "edit", "exit", "particles", "sound", "pos1", "pos2", "setstart", "setend", "title", "difficulty", "dynamic", "deploy", "undeploy", "save", "delete", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("difficulty")) {
            return filter(args[1], Arrays.asList("easy", "normal", "hard", "extreme"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("dynamic")) {
            return filter(args[1], Arrays.asList("addstate", "delstate", "interval", "list"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("particles") || args[0].equalsIgnoreCase("sound"))) {
            return filter(args[1], Arrays.asList("on", "off"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("deploy")
                || args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("delete"))) {
            return filter(args[1], new ArrayList<>(mapManager.getMaps().keySet()));
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


