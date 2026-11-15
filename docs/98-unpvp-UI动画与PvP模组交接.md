# unpvp 1.8.9：UI 动画与 PvP 模组交接

交接日期：2026-11-13。工作区：`/Users/ty/Documents/ChatGPT/启动器`。分支：`codex/unpvp-ui`。

本文描述当前代码与最近一次本地测试包，用于后续继续开发。此轮只新增交接文档，没有修改程序或重新发布。

## 1. 当前版本是什么

unpvp 是 Unbox 的第二版 Forge 1.8.9 客户端。现有功能统一进入 Unbox 自己的面板，使用自己的按键、配置与 HUD 编辑方式。

当前目录有 **29 个入口**：其中 **11 项功能来自 9 个公开项目的源码适配，17 项为 Unbox 原生功能，1 项为 Performance 管理入口**。这些入口不等于 29 个独立 JAR。外部性能引擎另行列出。

- 保留六项性能组件、Mac Retina 显示修复和已有启动/世界加载优化。
- 未加入 OneConfig、Essential 或其他客户端 UI 运行时。expvp 仅复用已获用户认可的 SVG 图标资产，没有采用其客户端代码架构。
- 不加入生存、建造、Replay、Freecam。Look Back 是切换回头视角，并非自由相机。
- 默认开启原有 FPS、CPS、Keystrokes、Latency；其他可开关模块默认关闭。Performance、Resource Packs 是打开页面的入口。
- 本地 macOS 调试包已构建并测试，未发布本轮线上更新；Windows 实玩尚未验证。

### 资料优先级

当前代码、构建产物和本文件优先于历史描述。细节可继续查看：

- [95：矢量 UI 与 PvP 整合](95-unpvp矢量界面与PvP整合.md)：第一轮移植及来源记录。
- [96：菜单响应与 Viewmodel](96-unpvp菜单响应与Viewmodel修订.md)：高斯模糊、切页衔接、Daytime、疾跑、改键、Viewmodel。
- [97：药水图标与界面缓存](97-unpvp药水图标与界面缓存优化.md)：最新渲染缓存、HUD 默认透明、药水图标及性能对照。
- [99：Mac 服务器大厅帧率调查](99-unpvp服务器大厅帧率优化.md)：后续记分板异常修复、密集实体实验及未纳入交付的字体优化。

95 中的“标题不模糊”“600ms 标题淡入”“240ms 列表过渡”“Favorites 顶部分类”“菜单中清除疾跑”均已被后续修改覆盖。96 中“关闭 UI 立即释放所有页面纹理”也已被 97 的有上限缓存策略覆盖。

**不要把 `src-tauri/resources/pvp-mods-lock.json` 或早期候选文档当成当前 unpvp 安装清单。** 它们包含旧方案候选及 OneConfig 等说明。当前应查 `scripts/unpvp_catalog.py`、`ports/pvp-independent/UPSTREAM.json`、`src-tauri/resources/unpvp-performance.json` 和 `src-tauri/src/unpvp.rs` 的实际安装路径。

## 2. UI 动画与视觉的最终状态

### 用户看到的行为

Right Shift 打开居中快捷菜单，再进入 Client Mods。目录顶部只保留 **PvP**；卡片收藏星标和收藏优先排序保留。HUD 编辑入口保留，退出编辑自动保存。

标题背景仍是原版 1.8.9 Panorama，增加均匀高斯模糊。Logo、按钮、文字在模糊之后绘制，保持清晰。该模糊与 PvP 的 Motion Blur 是两套独立功能：前者模糊标题背景，后者处理游戏运动画面。

| 项目 | 当前行为与参数 | 主要代码 |
| --- | --- | --- |
| 首次标题出现 | 120ms 淡入，三次缓出 | `SolidScreen.java` |
| 打开、关闭、返回及切页 | 保存上一帧，目标图层就绪后执行 120ms 交叉淡化；不先退到空白画面 | `UiTransition.java` |
| 设置页内部切换 | 已绘制页面重新布局时接入同一过渡；不等待上页淡完再开始准备新页 | `UiScreen.initGui()` |
| 目录内容变化 | 新旧列表图层交叉淡化 120ms；滚动本身不重建列表纹理 | `SolidScreen.atlasProgress()`、`VectorModsScreen.java` |
| 列表滚动 | 按实际帧时间推进的平滑滚动，系数 `1-exp(-dt*16)`；最终吸附到目标位置 | `VectorModsScreen.content()` |
| Hover、开关反馈 | 按实际帧时间趋近目标，系数 `1-exp(-dt*18)` | `Painter.Motion` |
| 减少动态效果 | 跳过页面交叉淡化、标题和列表渐变，并让滚动/反馈直接到位 | `SolidTheme.reduced()`、`Painter.Motion` |
| 缩放与窗口改变 | 重新按物理像素布局；旧过渡快照失效即释放，旧页面图层不拉伸；未就绪时不接受错位点击 | `SolidScreen.java`、`UiTransition.java` |
| 标题高斯模糊 | 横向、纵向两遍；每遍 25 个采样点，sigma 4、半径 12；中间目标为半窗口点尺寸，最终回到 Retina 帧缓冲 | `Backdrop.java`、`Panorama.java` |

这里的矢量 UI 是“SVG/路径按当前物理分辨率抗锯齿生成并缓存，再合成到游戏”。圆角控件另用解析边缘覆盖着色器。没有每帧解析 SVG，也没有嵌入浏览器。不能把这描述为所有内容都由实时 GPU 矢量引擎绘制，或保证任意显示设备绝对零锯齿。

### 为什么之前仍然有延迟

第一次修改用短过渡消除了空窗，并把高清像素转换移到后台，但仍有两项重复工作：

1. 关闭页面时删除共享文字、图标缓存，下页再次生成。
2. 即使 CPU 页面缓存命中，再次进入目录仍要重新上传整页高清纹理。

最新实现保留可复用的 GPU 页面纹理、文字和图标缓存；标题页空闲时准备快捷菜单和目录；常用设置文字提前在后台生成。点击卡片直接创建对应详情，去掉“先创建旧目录，再重建详情”的多余布局。

### 缓存生命周期与性能边界

| 缓存 | 预算/规则 | 所在代码 |
| --- | --- | --- |
| CPU 页面像素 | 128MiB，按场景、尺寸、密度及绘制内容匹配，最近最少使用淘汰 | `SolidSurface.java` |
| GPU 页面纹理 | 128MiB 缓存预算，通过引用计数复用；正在显示的工作纹理不能淘汰 | `SolidTexture.java` |
| UI 文字纹理 | 最多 512 项，同时限制 8MiB | `SolidText.java` |
| 矢量图标/形状遮罩 | 8MiB 预算；当前单个遮罩可保留 | `SolidGeometry.java` |
| 后台准备的文字像素 | 8MiB | `SolidText.java` |
| 过渡快照 | 过渡结束、目标变化、尺寸变化或减少动态效果时释放 | `UiTransition.java` |

预算不是整个进程的内存上限；极大窗口的当前工作纹理可能超出缓存预算。保留缓存用于快速重新打开菜单，不代表在世界中持续绘制菜单。世界中预热不主动上传整页 GPU 图层；标题页预热可上传。尺寸、GUI scale、主题变化会清理空闲缓存并重新准备。

注意 `UiWarmup.java` 当前类注释仍有旧的“snapshots never allocate GPU textures”措辞；实际行为应以 `SolidScreen.warm()` 为准：`mc.theWorld == null` 时会预上传。交接时仅记录此处注释偏差，未改动代码。

只有全屏不透明背景完整就绪且尺寸匹配时，才省略被遮住的世界绘制。透明界面或缓存未就绪时继续渲染世界。相关保护在 `SolidScreen.coversWorld()` 和 `UiWorldRenderMixin.java`，后续不得直接按“打开菜单”一概跳过世界绘制。

### 最近一次切页对照

同一 Mac、1280×800 窗口、Retina、GUI scale 2、相同六项性能组件；使用真实控件连续导航 4 轮。计时为触发导航至目标页首次完成绘制，**不含系统输入队列等待，也不等于淡化动画完成**。该对照在标题场景完成，不能当作世界 FPS 基准。

| 指标 | 修改前 | 当前版本 |
| --- | ---: | ---: |
| 首次快捷菜单 | 95.6ms | 28.1ms |
| 首次 Mod List | 47.0ms | 24.0ms |
| 首次 FPS 详情 | 64.3ms | 64.9ms |
| 后三轮切页中位数 | 27.5ms | 9.4ms |
| 后三轮最长切页 | 50.4ms | 19.8ms |
| 重复进入 Mod List 的纹理上传 | 每次 8 次 | 0 次 |

首次详情仍约 65ms，未解决为绝对零等待。测量原始数据：`.cache/unpvp-hud-response/timing.json`。后续继续优化应测量首次准备、重复导航、动画完成和世界 FPS，分别报告。

## 3. 哪些 PvP 功能来自公开模组

以下均为**选定功能的源码适配**，已接入 Unbox 配置与界面，编入 `unpvp-pvp.jar` 或核心 JAR；不是把完整上游客户端 UI 一起装进来。完整来源、固定提交与许可原文见 [UPSTREAM.json](../ports/pvp-independent/UPSTREAM.json) 和 [移植说明](../ports/pvp-independent/README.md)。

| 上游项目 | 固定版本/提交短号 | 当前面板功能 | 已采用范围及修改 |
| --- | --- | --- | --- |
| DamageTint | v3.3.0 / `a99fbc369cef` | Hit Color | 实际生物受伤叠色：颜色、透明度、随受伤淡出；不是手持物染色 |
| OverflowParticles | v1.0.2 / `1b84aba5285a` | Particles | 生成密度、数量上限、类型开关、大小、颜色；保留批量绘制，未移植完整粒子编辑/倍增功能，也未使用失败的现代版粒子实现 |
| OverflowAnimationsV2 | v2.2.5 / `cf947c9536f6` | Classic Animations | 部分 1.7 格挡、物品/弓/鱼竿位置及摇摆；去掉与 Antimations 重叠的装备重置拦截 |
| Antimations | `cbd1d81b574f` | Hand Animations | 装备重置、格挡、弓、进食、喝药等动作控制；换成 Unbox 配置 |
| VanillaHUD | v2.2.12 / `6ad9670b8402` | Scoreboard、Player List、Hotbar | 分数板真实队伍目标、隐藏项过滤、最多 15 条；Tab 数字延迟及头尾信息；快捷栏缩放/偏移等。分数板主体绘制在核心 `ExtraHud.java` |
| NoScreenShake | `513ec629ec8e` | View Bobbing | 分离世界和手部晃动，适配 OptiFine 的额外手部渲染路径 |
| PolyTime | v1.0.2 / `407008ed5ce2` | Time Changer | 本地时间；补齐天空角度、月相等直接读取时间的路径，修复真实夜晚/显示白天时闪烁；不修改服务器时间 |
| PolyBlur | v1.0.2 / `66ea9ae2bafe` | Motion Blur | 独立磷光残影着色器组及原始资源；默认关闭，关闭功能/进入菜单/离开世界释放，有 GPU 开销 |
| ItemPhysicLite | `c9635d9a1515` | Item Physics | 掉落物休止姿态及旋转适配；保留原版物品数量和批量绘制。固定源分支虽名为 1.12，却包含较新 MatrixStack 写法，不是原样编译的 1.8.9 源码 |

来源判断按代码与移植说明进行，不能因为文件放在 `ports/pvp-independent` 就把其中所有功能都算成外部模组。例如 Viewmodel、昵称、聊天、受伤视角强度的自制实现也有代码位于这里。

许可文件随构建产物保留在 `META-INF/unbox-pvp/`。不要删去署名、许可证或来源记录；当前本地测试集成不代表已经完成公开发布/商业分发审查。

## 4. 哪些功能由 Unbox 自制

“自制”指当前功能由 Unbox 的代码实现或从自身现代版原生适配，并不宣称相关功能概念最早由我们提出。

| 面板功能 / ID | 自制内容 | 主要实现 |
| --- | --- | --- |
| FPS / `fps` | 实际帧率显示、刷新间隔、样式 | `LiveHud.java` |
| CPS / `cps` | 左右键一秒滚动计数、按下反馈，不重复计算长按 | `LiveHud.java` |
| Keystrokes / `keystrokes` | 实际游戏绑定对应的按键与鼠标显示 | `LiveHud.java` |
| Latency / `ping` | 读取实际玩家延迟；本地显示 Local，无数据不伪造延迟 | `LiveHud.java` |
| Coordinates / `coordinates` | 坐标、方向、群系、维度及下界换算 | `ExtraHud.java` |
| Armor Status / `armor` | 实际装备、耐久数字/百分比、耐久条 | `ExtraHud.java` |
| Potion Effects / `potions` | 实际效果和剩余时间；最新加入原版状态图标，预览和 HUD 边界同步 | `ExtraHud.java` |
| Crosshair / `crosshair` | 准星形状、颜色、像素编辑与目标反馈 | `PvpControls.java`、`Preview.java`、`ModsScreen.java` |
| Toggle Sprint / `sprint` | 疾跑开关意图；进入菜单后保留，恢复游戏向前移动时继续 | `PvpControls.java` |
| Toggle Sneak / `sneak` | 切换潜行；菜单/失焦时仍清理潜行状态 | `PvpControls.java`、`SneakMixin.java` |
| Zoom / `zoom` | 按住/切换缩放、滚轮倍率和平滑过渡 | `PvpControls.java` |
| Look Back / `lookback` | 快捷回头与恢复原视角；没有复制 BehindYou 源码 | `PvpControls.java` |
| Nickname / `nickname` | 本地显示昵称，不改变账户、服务器身份或实际聊天身份 | `NameMixin.java` |
| Chat / `chat` | 外观、有限长度内存历史、Ctrl+F 搜索；玩家/系统分类是启发式，不写聊天历史到磁盘 | `ChatMixin.java`、`ChatHistory.java` |
| Hurt Camera / `hurtcam` | 缩放原版受伤旋转强度；没有集成 BetterHurtCam JAR/Essential | `CameraMixin.java` 的 hurt 钩子 |
| Resource Packs / `resources` | 自带材质包浏览器，查询兼容 1.8.9 的 Modrinth 资源包，校验下载，接原版启用/停用 | `PackBrowser.java` |
| Viewmodel / `viewmodel` | 第一人称手臂/手持物 XYZ 位置、25%–150% 大小、旋转；可单独设置空手 | `ViewmodelMixin.java` |

Viewmodel 研究过 OverflowAnimations 的公开自定义位置实现，当前采用自制的局部矩阵变换，没有复制其整套替换动画的方法或配置运行时。它与已有经典/手部动画组合，不修改实体大小、第三人称人物、攻击判定或交互距离。已检查空手、持剑、格挡和拉弓画面。

### 共用的自制交互

- `UiKeys.java`：面板直接编辑 Minecraft 实际按键。每个可切换模块有 Toggle module；Zoom、Look Back、Sprint、Sneak 另有 Activation key。新绑定清除冲突的旧绑定，Escape 取消，Delete 清除，支持鼠标键。
- `UiState.java` / `PvpSettings.java`：设置保存与运行功能之间的桥接，避免只更新面板而不更新游戏逻辑。
- HUD 编辑器复用实际 HUD 的测量/缩放数据，拖动与显示使用同一几何范围。
- 灰色 HUD 背景默认关闭：Latency、Coordinates、Armor、Potions、Keystrokes、Scoreboard、Chat；分数板标题底色也跟随 Background。原有显式保存的选择保留。FPS/CPS 没有常驻底色，按键反馈仍可显示。
- 输入文字、颜色、数字和快捷键的位置有实心灰阶底框。

**材质包入口不是 Resourcify。** 使用路径为 Right Shift → Client Mods → Resource Packs → Browse → Download → Installed → Enable。Browse 自动查询，下载后返回 Installed；下载不等于自动启用。当前不应向用户提供 Resourcify 的“原版资源包界面加号”使用说明。

## 5. 性能模组与依赖

性能清单以 `src-tauri/resources/unpvp-performance.json` 为准。这里是底层游戏优化，不能计入上述“9 个 PvP 源码项目”。

| 组件 | 版本/形式 | 用途 |
| --- | --- | --- |
| OptiFine | HD U M5，独立 JAR | 渲染和图形设置优化 |
| Nothirium 1.8.9 port | 0.4.9-beta-2，独立 JAR | 区块渲染优化 |
| Entity Culling 1.8.9 port | 6.5.0，独立 JAR | 遮挡实体剔除 |
| Phosphor Legacy Forge | 7，独立 JAR | 光照计算优化 |
| FoamFix | 0.6.3a，独立 JAR | 内存和相关性能优化 |
| Patcher · Unbox performance subset | 从 Sk1erLLC/Patcher 固定源码提取，独立本地 JAR | 选定的模型批处理、分配/查找与切换世界 GC 等优化；不是完整 PolyPatcher |
| RenderLib 1.8.9 port | 1.4.5.5 | 渲染引擎依赖及 Mixin 运行时 |
| ConfigurationsBackport | 1.1 | 配置依赖 |

前六项是“六个性能组件”，后两项是依赖，不另行包装为两项 FPS 功能。Patcher 子集省略外部社交/配置 UI、重复实体剔除等内容。来源和具体文件见 `ports/patcher-performance/UPSTREAM.json`，范围见其 README。

原版 Patcher、PolyPatcher 和当前 Unbox Patcher 子集不能混称；不要直接安装旧候选清单中的完整 PolyPatcher 来替代这个子集。

## 6. 文件地图与构建关系

核心目录：`client-mod/src/unpvp/java/dev/unbox/unpvp/`。

| 位置 | 用途 |
| --- | --- |
| `HomeScreen.java`、`VectorModsScreen.java`、`ModsScreen.java` | 标题/快捷菜单、目录、功能设置 |
| `UiTransition.java` | 页面交叉淡化与快照生命周期 |
| `SolidScreen.java`、`SolidSurface.java` | 页面布局、后台矢量生成、CPU 缓存、纹理调用 |
| `SolidTexture.java`、`SolidText.java`、`SolidGeometry.java` | GPU 页面缓存、文字、矢量图标/遮罩 |
| `UiWarmup.java` | 按尺寸/主题预热常用页面和设置文字 |
| `Painter.java`、`Edges.java` | 控件绘制、解析抗锯齿与交互反馈 |
| `Panorama.java`、`Backdrop.java` | 标题背景及高斯模糊 |
| `LiveHud.java`、`ExtraHud.java`、`Preview.java` | 实际 HUD、图标、预览与测量 |
| `scripts/unpvp_catalog.py` | 当前 29 个入口及设置默认值；构建时生成目录资源 |
| `ports/pvp-independent/src/main/java/dev/unbox/pvp/mixin/` | 外部适配和部分自制功能对游戏的实际接入 |
| `ports/pvp-independent/src/main/resources/mixins.unbox-pvp.json` | 功能钩子清单；required 为 true，接入失败应显式暴露 |
| `src-tauri/src/unpvp.rs` | 启动器准备、复制 JAR、依赖校验和启动 |

产物关系：

- `src-tauri/resources/unpvp-forge-1.8.9.jar`：Unbox UI、控制、HUD 等核心；复制到游戏实例时名为 `unpvp.jar`。
- `src-tauri/resources/unpvp-pvp.jar`：公开功能适配及 Viewmodel 等游戏钩子。
- `src-tauri/resources/unpvp-patcher-performance.jar`：独立 Patcher 性能子集。
- 性能清单中的外部 JAR 由启动器准备。不要把性能依赖完整塞进核心 JAR。
- 实例配置：`config/unpvp-ui.properties`；实际按键保存到 Minecraft 的 `options.txt`。不要覆盖玩家存档、账号或无关配置。

在已有工具链与依赖的本工作区构建：

```sh
python3 scripts/build-unpvp.py
python3 scripts/build-unpvp-pvp.py
python3 scripts/prepare-unpvp-performance.py
npm run tauri -- build --debug --bundles app
```

首次环境还需准备既有 Forge/Java 8 工具链及 RenderLib；若修改 Patcher 子集，再运行 `scripts/build-unpvp-patcher.py` 并重新准备性能清单。不要只构建核心后遗漏独立 PvP JAR。

本地应用：`/Users/ty/Documents/ChatGPT/启动器/src-tauri/target/debug/bundle/macos/Unbox Client.app`。

## 7. 验证证据及接手边界

所有游戏测试使用 `.cache/unpvp-tests/` 的独立身份与世界，不接触用户存档。

| 测试 | 最近结果 | 覆盖范围 |
| --- | --- | --- |
| `hud-response-final` | 94 项通过，退出 0 | 药水图标/边界、7 个透明背景默认值、既有 PvP/改键/疾跑/时间/资源包回归、缓存引用及彻底释放 |
| `hud-response-solid-small` | 19 项通过，退出 0 | 854×481、Auto、Retina；两主题、滚动、透明度、按钮交互和缓存清理 |
| `response-baseline-01`、`response-after-01`、`response-final-02` | 均 PASS、退出 0 | 同一 24 次真实控件导航及首个可绘制帧计时 |
| `nov13-delivery` | 历史 84 项通过，退出 0 | 上一轮 Daytime、疾跑、改键、Viewmodel 等修复的完整回归 |

最新包的三个 JAR 与最终实测文件一致，校验见 `.cache/unpvp-hud-response/bundle-audit.json`。截图及日志保存在对应测试目录；这些缓存目录不应被假定已提交到 Git。

可复用命令（每次换一个测试名称，脚本不覆盖旧测试目录）：

```sh
python3 scripts/test-unpvp.py handoff-response-01 --response --width 1280 --height 800 --mods .cache/unpvp-performance/jars
python3 scripts/test-unpvp.py handoff-pvp-01 --pvp --width 1280 --height 800 --mods .cache/unpvp-performance/jars
python3 scripts/test-unpvp.py handoff-small-01 --solid --width 854 --height 481 --gui-scale 0 --mods .cache/unpvp-performance/jars
```

脚本要求功能 PASS 且进程退出 0。过去曾出现“断言通过后关闭窗口原生崩溃”，不能仅搜索 PASS 就宣布成功。Mac 旧 Java/LWJGL 的 AppKit/`objc_msgSend` 偶发窗口崩溃在历史测试出现过，尚未证明彻底解决；最新上述测试均正常退出。详见 90、91、96 文档。

后续继续开发时：

- 优先处理首次详情页准备时间，并保留矢量清晰度；不要用延长淡化掩盖等待，也不要取消所有缓存导致重复上传。
- 增加 UI/设置时同步检查运行逻辑、默认值、预览和持久化，避免“面板有开关但功能未接入”。
- 更改目录/字体/窗口布局后，复查缓存键、缩放失效、点击命中及内存预算。
- 继续测试不同分辨率、连续开关菜单、不同资源包和真实服务器；Windows 和异地多人没有实玩结论。
- 95 中的历史世界 FPS 数值不能作为本轮切页缓存优化后的 FPS 成绩；本轮未建立新的世界 FPS 对照。
- 工作区有大量此前未提交修改；不要清理、回滚或覆盖其他任务文件。当前修改未合并、未推送，不自动获得公开发布授权。
