# 控制中心界面规格（LDLib 版交接）

材质在 `src/main/resources/assets/dashpanels_remote_control/textures/gui/`：
`connect_center.png`（主窗口 + 按钮三态）、`control_center_02.png`（信息框 + 三个长条按钮 + 底栏）、`channel_select.png`（面板选择器，220×158）、`widgets.png`（机械动力图集，仅取滚动条/箭头）。

## 已测量的像素坐标

### control_center_02.png
- 信息框：`x1..194, y1..70`（194×70）。边框 1px `#00C5A1`，内面 `#006C58`，框体 `#007E68`。
- 三个长条按钮：`(1,72) (30,72) (59,72)`，每个 28×13，间隔 1px。
  - 暗态：描边 `#84BC7D`、填充 `#548C54`
  - 亮态（选中）：描边 `#DBFFCF`、填充 `#8BE88B`
- 底栏（改名框/输入框那条）：`x1..134, y86..103`，上 1px 白、1px `#555555`，主体 `#000000`，下 1px 白。
- 框右侧两个小按钮：约 `x168..190`，`y8..30`、`y40..62`（原为定位按钮，可能要删）。

### connect_center.png
- 窗口主体：左 x2、右 x219（218 宽）。
- 标题栏 `y1..15`：1px 黑描边 + `#84BC7D` 高光 + `#548C54` 填充。
- 标题下暗带 `y16..31`。
- 装饰分隔线 `y32..37`（黑/白/`#C6C6C6`/白/`#555555`/黑 六行）。
- 内容区从 `y38` 起，1px 棋盘网点：`#006C58` / `#007E68` 交替（画的时候按 1px 交替填充即可，不必贴图）。
- 底栏区 `y200..244`，右下有小箭头。
- 右侧按钮三态列 `x≈240..255`：`y0..69` 一块、`y0..55` 两块（上下边界需再精扫；用途 = 行内按钮的普通/悬浮/按下）。

## 页面结构（用户原话整理）

标题栏下、分隔线上方：三个长条按钮，间隔 1px，分别切 **终端 / 遥控器 / 用户**。

### 终端页
- 框内从上到下：**名字、坐标、面板数、ID**
- 行内按钮：**断联**、**改名**
- 点改名 → 底栏出现改名框 + 确认 + 取消（改名框用 control_center_02 的那条）

### 遥控器页
- 框内：**名字、拥有者、ID**
- 行内按钮：**断联**、**指定**
- 点指定 → 打开 `channel_select.png` 面板选择器，勾选后把遥控器的 `drc:terminal` 指向该面板所在终端

### 用户页
- 底栏常驻：输入框 + 两个按钮（**可修改** / **仅操作**）
- 框左侧显示**玩家头颅**，右侧文本从上到下：**名字、权限、UUID**
- 行内两个按钮：**改权限**（点击在 可修改 ↔ 仅操作 之间切换并换图标）、**删除**

## 后端已经现成、LDLib 版可直接复用

界面只负责显示与发请求，这些都不必重写：

| 能力 | 位置 |
|---|---|
| 打开界面 | `PanelRemoteCenterBlock.useWithoutItem` 非潜行 → `CenterInfo.sendTo(player, center)` |
| 下发数据 | `net/CenterInfo.java` → `net/CenterInfoPacket`（含 `terminals[]`、`remotes[]`、`owner`、`access[]`、`canEdit`/`isOwner`/`network`） |
| 改名终端 | `net/RenameTerminalPacket`（服务端已 `enqueueWork` 主线程 + 回发新数据） |
| 删除终端 | `net/CenterRemoveTerminalPacket` → `PanelRemoteCenterBlockEntity.forgetTerminal(level, pos)` |
| 权限增删改 | `net/CenterAccessPacket`（`ADD` / `REMOVE` / `SET_LEVEL`，按玩家名，仅所有者可改） |
| 指定终端给遥控器 | `net/RemoteSelectPacket`；遥控器侧读写 `PanelRemoteData.terminal(ItemStack)` / `setTerminal(...)` |
| 遥控器记录（名字/拥有者/在线） | `PanelRemoteCenterBlockEntity.remotes()` / `recordRemote(...)` |
| 权限等级常量 | `PanelRemoteCenterBlockEntity.ACCESS_OPERATE = 0`、`ACCESS_EDIT = 1` |
| 终端有效性 | `rescanTerminals(level)`（方块在不在）、`servingTerminals(level)`（有没有东西可画） |

## 替换时的注意点

1. 现有 `client/CenterScreen.java` 是纯自绘，删掉即可；`client/TerminalRenameScreen.java` 也可以一并删，改名改在 LDLib 界面里。
2. **不要动** `client/PanelRemoteScreen.java`（3D 遥控界面）与 `client/RemoteListScreen.java`，它们不属于这次重做。
3. 三个长条按钮只有两态贴图（亮/暗），第三态（按下）用 `connect_center` 右侧那列；如果 LDLib 需要三张独立贴图，把右侧三块切成三个文件更省事。
4. `widgets.png` 是 Create 图集，坐标在 Create 的 `AllGuiTextures` 里，不是公开 API；建议只用它取滚动条/箭头，或干脆不用。