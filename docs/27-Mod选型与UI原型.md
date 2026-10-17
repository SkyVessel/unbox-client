# Unbox：模组选型、界面原型与实施闭环

调查日期：2026-10-14。目标环境：Minecraft Java **26.1 / 26.1.2 × Fabric / NeoForge**。本轮交付是调查、独立浏览器原型与验收清单；没有修改启动器、游戏模块、云服务或发布版本。

## 最新 UI 修订：以用户手绘要求为准

Review 05 已覆盖下文较早的深色面板和三列横卡提案：游戏内界面不再有包住内容的整块深色底板；正方形卡片直接悬浮在场景上，底部一体式开关条使用绿=开启、红=关闭。菜单按钮默认只有文字/图标，选择与 hover 才显示反馈。房主功能灰色标识，不做假开关。手机竖屏两列，可滚动；横屏和桌面更多列。Friends 及设置页同时去掉外层底板，启动器仍是独立应用布局。

新版：[局域网交互原型](http://192.168.31.158:8768/?v=5#mods)；[桌面画面](../design/unbox-v4-review/screenshots/v5-mods-1440.png)；[手机画面](../design/unbox-v4-review/screenshots/v5-mods-390.png)。这些仍是待评审原型，不是功能实现。

## 先看交付

- [玩家市场与商业模式](28-玩家市场与客户端商业模式.md)：补齐前一条市场、竞品和盈利问题。
- [交互原型](../design/unbox-v4-review/index.html)，本机预览地址 `http://127.0.0.1:8767`。原型所有界面英文。
- [原型说明与已覆盖交互](../design/unbox-v4-review/README.md)。它使用示例数据，不连接游戏或账号。
- [逐文件版本矩阵](../research/mod-review-2026-10-14/COMPATIBILITY.md)：精确游戏版本、加载器、发布类型、文件与依赖。
- [原始元数据](../research/mod-review-2026-10-14/)：作者项目页、Modrinth API 的公开发布元数据与许可资料快照。
- 原型画面见 `design/unbox-v4-review/screenshots/`；现状截图为之前真实游戏测试留下的画面，明确标了来源。没有伪造“已在游戏内实现”的 After。

**建议：优先集成成熟的功能引擎，Unbox 负责统一入口、个人配置、来源标识和恢复流程。不能为了少写代码，给 Fabric 和 NeoForge 留下两套完全不同、互相覆盖的设置系统。**

## 调查方法与证据等级

不是只看项目首页的加载器标签。每个候选查询 `/v2/project/<slug>` 与 `/version?game_versions=["26.1","26.1.2"]`，再分别筛选两个加载器，保留发布文件、日期、SHA-512、依赖与 release/beta/alpha。只纳入调查日期及之前的发布。

矩阵中的“有”只表示发布者为这个**精确组合**列出了产物，尚未安装到 Unbox 或运行实测。目标版本的依赖闭包、Java/加载器下限、Mixin 冲突、性能、存档迁移仍属实施验收。统一包的 API 依赖列表可能混合不同平台条件，不能把 Fabric API 直接装进 NeoForge；必须结合 JAR 内对应平台的元数据解析。

已有版本是 0.2.7。检查依据包括 `docs/25`、`docs/26` 和当前源码。旧的“26.1 仅 Fabric”“地图只有 Fabric”说明已被后续适配覆盖。

## 一、新增功能逐项结论

### N01 小地图：有成熟替代，但地图与社交共享要一起迁移

优先进入技术试接的是 [JourneyMap](https://modrinth.com/mod/journeymap)，并保留 [Xaero’s Minimap](https://modrinth.com/mod/xaeros-minimap) + [World Map](https://modrinth.com/mod/xaeros-world-map) 作为视觉偏好备选。

- JourneyMap 的四种目标组合都有产物，26.1 的候选为 beta。它有持久探索、大地图和公开的集成接口；符合替换当前内存地图的需求。
- Xaero 更贴近原版像素地图。26.1.2 的两个加载器都有文件，26.1 的 Fabric 有列明兼容文件，**此次没有找到列明 26.1 的 NeoForge 文件**，不能用 26.1.2 文件名来推断兼容或替用户升级游戏。
- [VoxelMap Updated](https://modrinth.com/mod/voxelmap-updated) 四组合有产物，但许可为 ARR，修改/分发条件与共享标记适配还需单独解决，暂不作为第一选项。
- 当前地图有本人与队友死亡点、按 UUID 的房间共享。第三方地图不会自动理解这套协议。替换前须做地图适配层：稳定世界标识、维度、队友位置和死亡点映射、重连、清理；保留原死亡记录做可回退迁移。
- 不同时启用两个地图渲染器或重复采样；保留未探明区域，不通过传送/生成区块来补地图。

授权直接影响实现：[JourneyMap 条款](https://teamjm.github.io/journeymap-docs/latest/about/licensing/)允许符合条件的整合包在安装/启动时从官方平台下载，禁止自行打包、再托管或修改。Unbox 不在其明确批准的启动器名单中，定制客户端的具体分发方式应在内置前明确。**不能把它放进 Unbox 安装包或通过房主文件中继发送给其他玩家。** Xaero 的项目说明允许有条件的整合包使用，但 CurseForge/Modrinth 以外的整合包商业变现需书面许可。两者的“现成 JAR”不等于“可以任意改成 Unbox 的专有模块”。

### N02 死亡墓碑：有现成方案，不能作为纯客户端开关

推荐按世界类型做一次实际对比，再确定默认：

| 候选 | 适用场景 | 当前边界 |
| --- | --- | --- |
| [Ketket’s Graves](https://modrinth.com/datapack/ketket-graves) 2.4+mod | 原版/轻量生存；四组合有数据包封装 JAR；MIT | 主逻辑在房主/服务端；物品组件、装备槽、死亡连发、虚空、卸载恢复要测。不要把跨加载器的数据包封装误称运行时转换 |
| [Graveless](https://modrinth.com/mod/graveless) | 26.1.2 模组生存；Fabric/NeoForge 均有原生产物；MIT | 是灵魂取回物品而非石碑方块，可能偏离你要的视觉；26.1 未列产物。作者描述包含额外保护效果，须能关闭再决定是否默认 |
| [Universal Graves](https://modrinth.com/mod/universal-graves) | Fabric 服务端；可让原版客户端参与 | 无 NeoForge；26.1 候选预发布；LGPL-3.0 |
| [GraveStone Mod](https://modrinth.com/mod/gravestone-mod) | 传统墓碑、NeoForge | 无 Fabric；客户端和服务端均需安装；ARR |

[Graves by Lullaby](https://modrinth.com/mod/ly-graves)虽然四组合都有封装，但作者明确写明会启用 keepInventory 且不兼容 Curios/Accessories，不宜作为大型整合包默认。数据包墓碑不能未经验证就承诺保管所有模组背包和饰品。

闭环：房主启用 → 备份与安装 → 重启世界 → 死亡生成 → 地图定位 → 按所有者 UUID 取回 → 背包满时无丢失/复制 → 重登仍能恢复 → 移除前检查未领墓碑。访客界面显示“Managed by host”，不能个人关掉服务器规则。

### N03 平滑上坡：已确认是像马一样直接走上一格方块

你补充的描述已明确需求：玩家遇到一格高路面时直接跨上去，不需要跳跃。不是仅让半砖/楼梯的镜头更平滑。

- 首选试接 [StepUpAgain2](https://modrinth.com/mod/stepupagain2)，MIT，四组合均查到发布文件。作者描述明确支持整格步高、普通行走和原版自动跳跃三种模式，蹲下恢复普通步高，按服务器记忆模式。项目文字仍部分描述旧版 Fabric 范围，故当前支持依据是逐文件声明，实施前还要核对目标 JAR 并实测。
- 完全对应“像马一样”的另一例是 [Jumpless Step](https://modrinth.com/mod/jumpless-step)：作者直接以骑马类比，MIT，1.0.0，仅查到 **26.1.2 NeoForge**；元数据要求客户端与服务端，因此不作为四组合统一默认。
- [Countered’s Smooth Steps](https://modrinth.com/mod/countereds-smooth-steps)主要改善步高内视角过渡，不能单独满足这次需求。

验收：1 格石块可连续走上；蹲下精确放置；与原版 auto-jump 互斥；关闭后恢复普通步高；服务器限制时有清晰反馈。它改变移动行为，不能把客户端模组理解为所有服务器都允许。不得引入鼠标平滑来掩盖旧输入问题。

### N04 观察者／灵魂出窍：Freecam 匹配，Freelook 不等价

[Freecam](https://modrinth.com/mod/freecam)提供相机脱离身体、返回原位；四组合均有 **1.5.0-beta.0**，许可 MIT。当前目标版本候选仍为 beta，不能标成稳定验证。

保留原 Freelook（转头看四周）并新增 Freecam（镜头自由移动），名字和按键分开。切换维度、死亡、断线、退出、打开菜单后都要能恢复控制。身体仍在世界中且可能受伤；不把它描述为服务器真正的旁观者权限。新服务器默认关闭，允许的私人世界可启用。

### N05 动态光源：LambDynamicLights 技术匹配，分发许可要先解决

[LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) **4.11.2+26.1.2**的发布元数据列明四组合支持，实际安装文件由平台元数据决定。可覆盖手持与实体光照。已经不必用旧的 NeoForge 非官方移植。

当前许可为 **Lambda License，不能沿用历史 MIT 印象**。本次额外从官方发布地址读取候选 JAR，仅在内存中检查：SHA-512 与发布元数据相符，内含许可证确实要求二进制分发的授权。未运行或安装该文件。证据：`research/mod-review-2026-10-14/lamb-license-inspection.json`。

[RyoamicLights](https://modrinth.com/mod/ryoamiclights)较旧，此次无目标文件；不能作为省事的 26.1 替代。原型展示目标效果，不表示已获授权。实施须测手持物品、掉落物、多人、Sodium/Iris 配对与密集光源；视觉亮度不改变服务端刷怪规则。

### N06 建筑 mod 合集：建议三个层级，避免全装

| 层级 | 推荐调查结果 | 启用方式 |
| --- | --- | --- |
| 日常辅助 | Fabric：Litematica + MaLiLib；NeoForge：[Forgematica](https://modrinth.com/mod/forgematica) + MaFgLib | 原生加载器分别编译的项目，非 Fabric 兼容层；投影、材料清单、缺块检查优先；NeoForge 的目标候选为 beta |
| 生存放置工具 | [Effortless Building](https://modrinth.com/mod/effortless-building) 4.3 | 26.1.2 双加载器，26.1 未查到；房主与参与者安装，消耗材料、撤销和权限由服务端验证 |
| 创造编辑 | [WorldEdit](https://modrinth.com/plugin/worldedit)，Axiom 作为另选 | WorldEdit 四组合有文件，需要相应权限；[Axiom](https://modrinth.com/mod/axiom)本轮仅 Fabric，闭源和商业条款需进一步确认，不默认加入生存体验 |

[Litematica 作者说明](https://modrinth.com/mod/litematica)与 Forgematica 页面均提示 1.21.3+ 的光影投影问题。闭环不能只做“安装成功”：开始投影时说明冲突 → 允许临时关闭光影 → 记录原选择 → 完成/取消后恢复。原型给出了此入口，真正的 Iris 切换仍待实施。

MiniHUD / Tweakeroo 是 Fabric 可选候选，先避免与 Unbox 的坐标、HUD、Freecam/按键重复。不要默认带自动打印或越权放置。大批量编辑、区块卸载、断线后撤销都需要世界级测试；撤销编辑代码不等于回滚世界。

### N07 3D 物品掉落：先区分掉落物理与物品模型

[ItemPhysic Lite](https://modrinth.com/mod/itemphysic-lite) **1.6.11**，四组合有产物，LGPL-2.1，依赖 CreativeCore。适合物品落地平躺/旋转的纯视觉改进，不改变服务端掉落实体规则。

**它不会给每个二维物品自动制作完整立体模型。** 如果你指“剑、食物等都变成立体模型”，还需要专门模型/资源包以及模组物品覆盖；此项应单独选图，不以 ItemPhysic Lite 假装全部解决。Physics Mod 虽有目标文件但范围更大、性能与许可成本更高，不作为本轮默认。

### N08 背包整理：优先 Inventory Profiles Next

[Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next) **2.3.8**，四组合有发布文件，客户端侧，支持整理、锁槽、同类转移。许可 AGPL-3.0-or-later；保持独立项目、保留作者与源码入口，若改动/结合代码须遵守对应许可，不能当作 Unbox 独占代码。

libIPN 为依赖；Fabric 还涉及 Kotlin/Fabric API 等，必须按锁定版本解决传递依赖。[Inventory Sorting](https://modrinth.com/mod/inventory-sorting)也是候选且 MIT，但服务端必需，因此不适合作为“去任意普通服务器都能用”的默认替代。[Mouse Tweaks](https://modrinth.com/mod/mouse-tweaks)可改善鼠标搬运，不能单独替代完整整理。

闭环：开关与改键 → 原版/模组容器识别 → 排序 → 锁定工具栏 → 延迟/服务器拒绝后回到真实物品状态。不允许幻影物品、物品复制、丢弃误操作；对不能确认的特殊 GUI 禁用自动整理。

### N09 分数板：有现成 Fabric 方案，跨加载器建议 Unbox 统一实现外观

[Scoreboard Overhaul](https://modrinth.com/mod/scoreboard-overhaul)支持背景颜色、位置、缩放、变化时显示；[VanillaHUD](https://modrinth.com/mod/vanillahud)可调分数板标题/主体颜色、分数数字和其他 HUD。二者的目标文件均为 Fabric，不能借项目总平台标签推断 NeoForge 可用。

如果必须四组合交互一致，推荐在现有 Unbox HUD 中添加**只修改显示**的 Scoreboard 模块；可参考这些项目的交互，若复用源码则遵守许可证。不要为这一个组件引入与现有 HUD 重叠的完整编辑器。

设置至少包括背景色、透明度、文字阴影、缩放、位置、标题/数字显隐、预览。没有服务器 scoreboard 时显示“世界尚无分数板”的设置预览，正常游戏不凭空生成排行；不修改服务器分数。原型已提供颜色、透明度、数字显隐和 HUD 拖动。

### N10 Nickname：确有本地改名项目，但未查到目标版本的直接替换

[NameHider](https://modrinth.com/mod/namehider)的功能语义最接近：仅本机自己的显示名，不改 UUID、GameProfile、认证或网络。许可 MIT，但此次**26.1 / 26.1.2 都无发布文件**。PlayerNameHider 目标版本也未覆盖。NameFabric 实际是查询名字历史/皮肤的工具，不符合需求；Simple Nicknames 是服务端昵称机制，也不符合“仅自己可见”。

建议 Unbox 自身加小型本地别名模块，或基于许可允许的 NameHider 代码做原生双端适配。按账号 UUID 定位自己，不能通过全局替换字符串误改同名玩家、好友身份和聊天消息。原型把聊天真实身份保留；是否还要隐藏截图里的自己的聊天名字需另定义显示层范围，不能重写签名或发出的消息。

闭环：输入 → 即时本地预览 → 保存 → 切换账号隔离 → 重登持久化 → 一键还原；第二位玩家确认仍看到真实名称，好友/存档身份保持一致。

## 二、旧模块能否直接替换

| 旧模块 | 现成方案 | 结论 |
| --- | --- | --- |
| 小地图 | JourneyMap / Xaero / VoxelMap Updated | 值得换引擎；先完成许可、四组合覆盖、世界标识与共享死亡点适配；不同时渲染新旧地图 |
| 准星绘制 | [PolyCrosshair](https://modrinth.com/mod/crosshair)；[Custom Crosshair Mod](https://modrinth.com/mod/custom-crosshair-mod) | PolyCrosshair 对像素画布、撤销、预设最匹配，但目标仅 Fabric，依赖 OneConfig 与 Kotlin；Custom Crosshair 目标 26.1.2 双加载器，26.1 NeoForge 缺文件，且 ARR。推荐保持 Unbox 跨加载器绘制核心、重做编辑器；如选择完全替换，必须接受移植和授权工作 |
| 粒子数量/颜色/尺寸 | [OverflowParticles](https://modrinth.com/mod/overflowparticles) | 功能很贴近旧模块，目标仅 Fabric，依赖 OneConfig；不是四组合直接替换。参考其分类和实时预览，Unbox 原生重构或许可兼容的移植二选一 |
| 粒子性能 | [Particle Core](https://modrinth.com/mod/particle-core) | 四组合有文件、MIT；可作为性能候选，但不提供完整外观编辑器。必须避免和原 `ParticleFilter` 重复筛选/重复乘数量；增加与性能前后对照 |
| 粒子装饰 | Visuality / Particular / FBP Renewed / AAA Particles | Visuality 目标仅 Fabric；Particular 此次无目标文件；FBP 是方块粒子且目标 beta；AAA 是效果库。均不是原外观设置的完整替代，不默认叠加 |

当前 `CrosshairPattern.java` 已有绘制存储，`ModOptions.java` 与启动器导出配置涉及 248 个选项。移除启动器 Client Mods 页面前，必须建立旧选项 → 游戏内新入口的逐项映射，确保已有配置仍能读、不能悄悄恢复默认。原型没有把 248 项一一绘制，完整设置迁移是实施验收项，不能声称已经覆盖。

## 三、UI 原型方向与现状证据

### 当前问题的可定位路径

- `FriendsScreen.java` 的 `extractBackground` 只调用模糊和填色；标题画面本身在 `UnboxHomeScreen.extractBackground` 中绘制 panorama。打开独立 FriendsScreen 后没有继续绘制父标题 panorama，是“菜单脱离原背景”的明确可疑路径。此次是源码定位，**未启动当前游戏复现全黑，也不把它写成已经修复**。
- HUD 文本经过 `PanelStyle.hudLabel → AtlasText`；统一改像素字体必须连同宽度计算、居中、拖动边界和缩放一起改。
- `UnboxHomeScreen` 与 `UnboxScreen` 都有蓝色拖动描边和 `Drag to move` 提示，必须两处一起处理。
- 当前 `SkinManager` 支持一份上传预览/应用，不等于多皮肤库；`OptifineCape` 不等于 Unbox 自有披风服务。

### 画面提案

1. 沿用 Right Shift 的场景连续感、开盒标志、克制尺寸。黑白灰为控件主色，蓝色只用在选择和主要动作。所有按钮无边框，hover 背景贴合按钮；键盘焦点仍清晰。
2. Mods 使用三列较扁卡片：图标 + 名称 + 一句用途 + 独立开关；卡片点击进配置，开关点击只切换。内置个人模块与世界玩法状态分明。
3. Friends 在原标题场景上显示半透明紧凑列表，中央 Unbox 标题隐藏，关闭后返回标题；不把 Right Shift 整套中心按钮搬进 Friends。失败时保留缓存列表并提供 Retry。
4. HUD 使用像素文字，选中/移动时白色细描边，移除悬浮 Drag to move 文案；支持键盘微调、重置和完成。原型字体为 OFL 的 Silkscreen，**只用于验证方向**；游戏中优先复用原版像素字体，另测中文、资源包字体和 GUI 缩放。
5. 准星：左画布右场景预览、实际大小副预览、笔/橡皮、撤销、预设、颜色、导入导出。实际游戏实现须确保预览与游戏使用同一绘制逻辑。
6. 粒子：选类型 → 数量/透明度/尺寸 → 局部预览；不在设置界面给世界刷真实实体。原型是图形示意，不冒充真正的 Minecraft 粒子。
7. 重做 21 项模块图标，24 单位网格统一线宽。开盒 Logo 复用当前源码的原始路径，不重画品牌。
8. 背景只保留真实标题 panorama/当前世界；本次浏览器使用项目已有场景图代替。移除无必要的重复全屏背景层，而不是让页面变成全黑；降低透明度模式仍可改为实色。

## 四、启动器、Profile 与共享内容

你的补充已明确：**像你使用官方启动器的体验，切换游戏版本后仍看到同一批本地存档，并能选择进入；材质与模组资源不因切换 Profile 而无故消失。** 不再把它列为待确认问题。

核对 Lunar 官方资料后，需要区分可确认的事实与产品提案：

- [Lunar 的版本设置说明](https://www.lunarclient.com/news/how-to-launch-badlion-client-on-lunar-client-step-by-step-guide)展示版本选择器和各版本 Game Directory 设置（这篇讲其集成的 Badlion 入口，不冒称直接证明所有 Lunar 模式的默认目录）。[官方更新日志](https://www.lunarclient.com/changelog)记录 2026-10-03 修复在 `.minecraft` 与其他游戏目录之间移动后遗留 mods 的问题，并有 Profile 导出入口。可确认它支持版本、Profile 和游戏目录管理；不能据此断言所有模式永远共用同一个默认目录。
- [早期官方说明](https://www.lunarclient.com/news/patch-notes-9)有跨版本持久设置；它证明设置可跨版本保留，不能单独当作世界存档共享证据。
- 存档是否一起显示，关键是游戏实际读取的世界库，而不只是版本按钮。Unbox 应按你明确的体验目标设计：版本是启动选择；世界库默认共同可见，支持用选中版本进入。兼容检查只处理实际存在的升级/内容缺失问题，不把世界永久锁定在某个 Profile。

原型已补上：切换 26.1 / 26.1.2 后世界列表保持；`Old home` 可从 26.1 通过 **Back up & open** 用 26.1.2 进入；加载器不匹配且依赖玩法模组的 `Workshop` 才提示切回原环境。这里的备份、检查与进入均为交互演示，没有改动真实存档。

设计原则：

- 默认所有本地世界共同可见；切换版本不复制一套空存档。记住上次保存版本与必需内容；兼容时直接用当前版本进入，升级先备份再进入。加载器缺少玩法内容时提供原环境，降级不直接覆盖。加载器不同本身不等于纯原版存档不能打开。
- 缓存可按内容哈希共用，运行环境仍隔离。**不把不同加载器的 mods/config/saves 目录直接硬链接成同一个可写目录**；同一世界有运行锁。
- 旧 Profile 不删除：第一次迁移预览映射、备份、核对世界数量与哈希，再建立库记录。失败回到原路径。
- 启动器移除个人 Client Mods 设置入口；游戏未启动时保留“在游戏中 Right Shift → Mods”的说明。游戏标题页也能进入 Mods，不要求先加载世界。
- 启动器 Mods / Packs 内容管理仍保留，用于安装、更新与来源查看；它与个人 HUD 设置是不同职责。

**房主 / 自己 两列表**对 Mods、Resource packs 分别提供：来源、作者/来源页、版本、启用状态、强制/可选、下次重启是否变化。

| 情况 | 用户看见什么 | 实施规则 |
| --- | --- | --- |
| 房主要求的玩法 JAR | From host / Required | 访客不能在当前房间单独禁用；准备前检查许可与依赖 |
| 个人 HUD / 画质 JAR | Yours / Personal | 不被房主配置覆盖；允许服务端规则限制相关功能 |
| 相同 mod ID 与相同哈希 | Yours + From host | 一份文件一次加载；双来源标识，不重复装 |
| 相同 mod ID 不同版本 | 明确冲突，Use host version / Cancel | 只改共享会话副本；退出恢复个人环境；绝不同时装两个冲突版本 |
| 需要重启的增删 | Pending restart | 保存并退出 → 准备 → 重启 → 自动加入，不伪装热加载 |
| 房主材质优先顺序 | From host / Priority | 按世界要求组合资源包栈；退出后恢复个人顺序，不覆盖原 options |
| 下载失败/校验不符 | 文件名、原因、Retry / Cancel | 只有完整校验通过才替换会话；保留可启动的旧状态 |
| 禁止再分发的项目 | Official download required | 使用官方项目/版本来源，不通过房主中继或自建镜像再次分发 |

## 五、皮肤与披风闭环

皮肤库：导入 PNG → 校验 64×64/64×32 与大小 → 选 Classic/Slim → 本地命名与保存 → 预览 → Apply → Minecraft 身份认证 → 上传成功确认 → 提醒重新加入。失败/登录失效保留本地皮肤并重试；删除使用可恢复操作。64×32 老皮肤只用 Classic，实施与原型均须尊重此限制。每个账号的应用状态独立，多皮肤不等于为玩家创建多个游戏账号。

原型可添加、选择、改名、移除/撤销与演示应用失败；大模型人物图是现有资产的**示意预览**，并不展示导入 PNG 后的真实 3D 蒙皮。正式实施沿用已有 skinview3d，将库选择真正绑定预览和上传。

自有披风：原型提供 Mono / Blue 两种开盒视觉草案；选择 → 预览 → Apply → 按稳定 UUID 同步。还需实际的披风资产、所有权/授权、缓存、同步接口和游戏渲染，不能用现有 OptiFine 开关代替。

可见性需明确：Unbox 披风默认只在 Unbox 用户间可见；不能使原版客户端自动显示。官方、OptiFine、Unbox 披风优先级可选，最终只渲染一个。若未来售卖，所有权由服务端核实，客户端改本地 JSON 不能解锁。

## 六、每项需求的跟踪与验收

所有项目此刻均为“调查/原型完成或已说明边界，产品功能未实施”。批准后按下表逐项交付，不能只用“编译成功”结项。

| ID | 需求 | 原型/研究入口 | 实施后最少验收 |
| --- | --- | --- | --- |
| N01 | 小地图替换 | Mods / Minimap；选型 N01 | 四组合启动、持久地图、维度/重连、队友/死亡点迁移、性能开关对照 |
| N02 | 墓碑 | Mods / Gravestones；选型 N02 | 同名不同 UUID、虚空/岩浆、多次死亡、满背包、模组槽、取回后重登、卸载 |
| N03 | 平滑上坡 | Mods / Smooth Step | 马式一格步高；蹲下、自动跳、服务器拒绝、关闭恢复 |
| N04 | Freecam | Mods / Freecam | 身体不移动、死亡/切维度/断线退出、按键不冲突 |
| N05 | 动态光源 | Mods / Dynamic Lights | 许可通过；Sodium/Iris；密集光源帧时间；关闭释放资源 |
| N06 | 建筑合集 | Mods / Building Tools | 投影导入、材料列表、旋转/分层、缺块验证；光影恢复；服务端权限/材料/撤销 |
| N07 | 3D 掉落 | Mods / Dropped Items | 确认物理/模型范围；堆叠物品与模组模型、拾取、开关、性能 |
| N08 | 背包整理 | Mods / Inventory Sort | 快捷键、锁槽、特殊容器、延迟与服务端拒绝、无复制/丢失 |
| N09 | 分数板 | Mods / Scoreboard → HUD | 颜色、透明度、缩放、移动、无数据、长文、真实分数不变 |
| N10 | 本地昵称 | Mods / Local Nickname | 仅本人、按 UUID、账号隔离、还原；另一客户端仍见真实身份 |
| U01 | 像素 HUD | HUD | 所有 HUD 文字及宽度计算、不同 GUI scale、长名字和非拉丁字符 |
| U02 | 白描边/去提示 | HUD | Shift 快捷页及完整编辑页两路径；鼠标/键盘、锁定、边界、恢复 |
| U03 | 准星重做 | Crosshair | 绘制/擦除/撤销/预设/导入导出、真实大小、开关只渲染一份 |
| U04 | 粒子重做 | Particles | 按类型保存与预览一致；游戏效果、数量预算、不开启重复过滤 |
| U05 | 图标/卡片 | Icons / Mods | 21 项一致性、焦点/hover、点击卡片与开关不串事件、可访问名称 |
| U06 | 版本入口/共享 Profile | Launcher | 旧 Profile 迁移、共同发现但兼容隔离、世界锁、备份与回退 |
| U07 | 统一 UI/去背景/配色 | Shift menu / 全部页 | 黑白灰蓝、无边框、减少透明度/动态效果、小窗/普通窗/全屏 |
| U08 | 标题 Friends | Friends | 原背景持续、中央标题隐藏、关闭恢复、请求/邀请/离线/失败重试 |
| C01 | 启动器移除 Client Mods | Launcher / Mods | 248 项旧设置迁移检查；游戏标题和世界中都可配置 |
| C02 | Host / Yours 内容 | Shared content | JAR与材质两列表、双来源、冲突、退出恢复、许可来源和失败重试 |
| C03 | 多皮肤/披风 | Wardrobe | PNG校验、真实3D、身份/应用、删改/恢复、披风可见性/优先级/同步 |

建议实施顺序（尚未授权执行）：

1. 你先评审本次界面、图标与交互；共享存档和马式上坡已按你的补充确定。
2. 建立四组合候选锁定清单与许可决策；用复制世界做最小集成验证，先不合并所有模组。
3. 统一游戏内菜单与旧配置迁移，完成分数板、本地昵称、准星/粒子的共同实现。
4. 逐项集成地图、整理、物品视觉、Freecam、步高；动态光源满足授权后再内置。
5. 加入墓碑/建筑世界功能并验证恢复，再落地版本入口、共享库迁移和来源列表。
6. 多皮肤和披风作为独立验收项；最后联合回归四组合、macOS/Windows及双客户端，发布另按用户授权执行。

## 七、图片与交互参考

以下第三方素材只作为参考链接，不纳入 Unbox 分发资产；示例 UI 可能对应其他版本，**不能用图片证明 26.1 兼容**。

| 编号 | 原作者来源 | 图片/交互参考 | 借鉴内容 | 状态 |
| --- | --- | --- | --- | --- |
| R01 | [PolyCrosshair](https://github.com/Polyfrost/PolyCrosshair) | [绘制动图](https://raw.githubusercontent.com/Polyfrost/PolyCrosshair/main/.github/images/editor.gif) | 像素画布、镜像与撤销 | 调研参考，未使用代码/资产 |
| R02 | [OverflowParticles](https://github.com/Polyfrost/OverflowParticles) | [分类动图](https://raw.githubusercontent.com/Polyfrost/OverflowParticles/main/.github/images/settings.gif) | 类型列表、每类设置 | 调研参考 |
| R03 | [Scoreboard Overhaul](https://gitlab.com/horrificdev/scoreboard-overhaul) | [效果图](https://gitlab.com/horrificdev/scoreboard-overhaul/-/raw/main/docs/screenshot.png?v=1) | 位置、透明背景、分数布局 | 调研参考 |
| R04 | [Graveless](https://github.com/breakinblocks/Graveless) | [灵魂取回图](https://raw.githubusercontent.com/breakinblocks/Graveless/main/metadata/images/hero-ghost.png) | 物品恢复的可视目标；不同于石碑 | 备选视觉 |
| R05 | [Ketket’s Graves](https://modrinth.com/datapack/ketket-graves) | [墓碑示例](https://github.com/user-attachments/assets/a66a64dc-22f9-4187-84ee-229956c6f4e4) | 传统墓碑外观 | 备选视觉 |
| B01 | 本地 0.2.7 Fabric 26.1.2 | [现状 Mods](../design/unbox-v4-review/assets/before-mods.png) | 当前实现基线 | 实际截图 |
| B02 | 本地 26.1.2 UI 检查 | [现状准星](../design/unbox-v4-review/assets/before-crosshair.png) | 当前实现基线 | 实际截图 |
| P01 | 本轮独立原型 | [Mods](../design/unbox-v4-review/screenshots/mods.png) | 新卡片与图标 | 待用户评审 |
| P02 | 本轮独立原型 | [Friends](../design/unbox-v4-review/screenshots/friends.png) | 原场景上的朋友列表 | 待用户评审 |
| P03 | 本轮独立原型 | [Crosshair](../design/unbox-v4-review/screenshots/crosshair.png) | 绘制与实际大小 | 待用户评审 |
| P04 | 本轮独立原型 | [HUD](../design/unbox-v4-review/screenshots/hud.png) | 像素字、白描边 | 待用户评审 |
| P05 | 本轮独立原型 | [Icons](../design/unbox-v4-review/screenshots/icons.png) | 原创模块图标 | 待用户评审 |
| P06 | 本轮独立原型 | [Shared content](../design/unbox-v4-review/screenshots/content.png) | 来源两列表 | 待用户评审 |
| P07 | 本轮独立原型 | [Wardrobe](../design/unbox-v4-review/screenshots/wardrobe.png) | 多皮肤库 | 待用户评审 |

查阅日期统一为 2026-10-14。没有以下载量代替当前活跃人数，也没有宣称本轮候选是全网排名。

## 八、本轮验证记录

浏览器原型已完成 41 项布局/交互检查，加上用户澄清后 5 项检查：跨版本备份进入、降级处理、玩法加载器不匹配、64×32 皮肤限制、标题页选世界邀请。检查结果见 `design/unbox-v4-review/verification.json`；新增检查全部通过，无页面脚本异常。实际游戏模组兼容性、真实备份与联机未在本轮执行。

[现状与提案对照图](../design/unbox-v4-review/screenshots/comparison.png)。提案是在浏览器里制作的界面，不是已经改好的 Minecraft 截图。
