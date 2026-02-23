# SITParkourWarrior 使用指南

面向 Paper 1.21+ 的跑酷插件。

## 环境要求
- Java 21+
- Paper/Spigot 1.21+
- WorldEdit

## 安装
1. 将插件 jar 放入 `plugins/`。
2. 将 WorldEdit 一并放入 `plugins/`。
3. 启动服务器。
4. 首次启动后会生成数据目录：`plugins/SITParkourWarrior/`。

## 命令总览
命令前缀：`/sitpkw`

- `/sitpkw create <id>`：创建关卡并进入编辑模式。
- `/sitpkw edit <id>`：编辑已有关卡。
- `/sitpkw exit`：退出编辑模式。
- `/sitpkw pos1`：设置选区点 1（当前坐标）。
- `/sitpkw pos2`：设置选区点 2（当前坐标）。
- `/sitpkw setstart`：设置起点（当前坐标）。
- `/sitpkw setend`：设置终点（当前坐标）。
- `/sitpkw title <文本>`：设置关卡标题。
- `/sitpkw difficulty <easy|normal|hard|extreme>`：设置难度。
- `/sitpkw particles <on|off>`：开关关卡粒子效果。
- `/sitpkw sound <on|off>`：开关关卡音效。
- `/sitpkw save <序列id>`：将当前选区保存为一个动态状态 schem。
- `/sitpkw dynamic addstate <文件名> [间隔tick]`：加入状态并设置该状态停留时长。（不推荐使用）
- `/sitpkw dynamic delstate <序号|文件名>`：删除一个状态。
- `/sitpkw dynamic interval <序号> <tick>`：修改某个状态的间隔。
- `/sitpkw dynamic list`：查看动态状态列表。
- `/sitpkw deploy [id]`：在当前位置部署关卡。
- `/sitpkw undeploy [id]`：回收当前位置所在部署。
- `/sitpkw delete <id>`：删除关卡。
- `/sitpkw reload`：重载所有关卡数据。

## 快速上手（推荐流程）
1. `/sitpkw create parkour_01`
2. 用 `/sitpkw pos1`、`/sitpkw pos2` 设定关卡区域（也可以使用线进行选区）。
3. 在起点位置执行 `/sitpkw setstart`。
4. 在终点位置执行 `/sitpkw setend`。
5. 执行 `/sitpkw title 标题` 和 `/sitpkw difficulty easy`（设置难度）。
6. 需要动态结构时：
   - 选区后执行 `/sitpkw save 1`（会保存 schem）
   - 可继续添加多个状态，并用 `/sitpkw dynamic list` 检查。
7. 到目标位置执行 `/sitpkw deploy parkour_01`。