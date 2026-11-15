# 高版本 PvP 客户端模组清单

> 2026-11-14 集成记录见 [96-1.21.11矢量UI与FabricPvP接入](96-1.21.11矢量UI与FabricPvP接入.md)。下文保留原选型要求；实际版本、BactroMod 功能差异、原生 Viewmodel 优先、Look Behind 源码适配及资源包不捆绑的处理以 96 和锁文件为准。

更新日期：2026-11-13。首个核对目标为 Minecraft Java 1.21.11 Fabric；其他游戏版本和加载器须分别核对公开发行文件。本文是待集成清单，不表示已经安装、适配或通过测试。

## 复用 Unbox 现有功能

PvP 列表中共享现有的 **FPS、CPS、Ping、Keystrokes、Armor Status、Auto Sprint**，不再安装同功能的独立模组。各目标版本仍需验证实现及 HUD 编辑、配置保存。

## 需要集成的公开项目

| 项目 | 需要提供的功能 | 选用和去重说明 |
| --- | --- | --- |
| [Crosshair Addons Public](https://modrinth.com/mod/crosshair-addons-public) | 鞘翅穿戴、烟花、投射物命中和目标等准星提示 | 保留指定项目；与 Better Hitreg 准星提示重复时只启用一套。 |
| [Shield Fixes](https://modrinth.com/mod/complete-shield-fixes) | 修正盾牌声音及格挡动画 | 与 BactroMod 低盾牌和盾牌冷却显示联合检查。 |
| [Better Totem Counter](https://modrinth.com/mod/better-totem-counter) | 身上图腾数量、玩家图腾触发次数 | 图腾计数选这一项，不再加入其他计数器；需要 Fabric API 和 DTS Lib。其小图腾动画与 BactroMod 重复，默认只启用 BactroMod 的对应功能。 |
| [Status Effect Timer](https://modrinth.com/mod/T9FDHbY5/) | 原版状态效果图标显示剩余时间及等级 | 不再加入功能相同的药水计时 HUD。 |
| [AppleSkin](https://modrinth.com/mod/appleskin) | 食物恢复量、饱和度等信息 | 选取目标版本的公开文件，检查多人服务器数据同步限制。 |
| Combat Hitboxes | 目标碰撞箱、线条及颜色显示 | 保留用户指定的 [原作者项目](https://modrinth.com/user/sootysplash/mods)，不再同时加入 Combat Hitboxes+；具体 1.21.11 文件及依赖待核实。Better Hitreg 自带碰撞箱显示若重复则关闭。 |
| [Natural Motion Blur](https://modrinth.com/mod/natural-motion-blur/) | 可调动态模糊 | 默认关闭；验证与 Iris、光影、帧时间的兼容性。这是画面效果，不作为鼠标平滑或视角卡顿修复。 |
| [BactroMod](https://modrinth.com/mod/bactromod) | 全亮、低火、低盾牌、部分雾效控制、物品缩放、小图腾触发动画 | 保留一个来源，不再加入独立低火、低盾牌或小图腾动画模组；确认实际设置能满足需求。 |
| [Shulker Box Tooltip](https://modrinth.com/mod/shulkerboxtooltip) | 在物品提示中预览潜影盒内容 | 只选此项目，不再加入其他潜影盒预览模组；逐加载器确认发行文件。 |
| [Theone’s Eating Animation Pack](https://www.planetminecraft.com/texture-pack/theone-s-eating-animation-pack/) | 食物和饮品动画 | 这是资源包，不是 JAR；作为独立可选资源包支持。作者标注保留所有权利，分发前核实许可。 |
| [Elytra Swapper+](https://modrinth.com/mod/elytra-swapper-mrdefault) | 按键切换鞘翅和胸甲，搜索背包、快捷栏及副手 | 只选一个切换模组；其自动物品栏操作应遵守服务器规则。 |
| [Elytra Pitch](https://modrinth.com/mod/elytrapitch) | 飞行俯仰角，可选速度、高度、方向和耐久 | 只选一套飞行角度显示；锁定角度和自动调整属于额外辅助，单独标识并遵守服务器规则。 |
| [TweakHands](https://modrinth.com/mod/tweakhands) | 主副手位置、旋转、缩放和物品类型设置 | 完整手部变换以此为主；与 BactroMod 缩放及低盾牌重复的变换只启用一套。 |
| [Look Behind](https://modrinth.com/mod/look-behind) | 按键向后看并继续前进 | 与现有 Freelook 的按键、视角行为分别处理，不重复添加其他回头模组。 |
| [Shield Cooldown HUD](https://modrinth.com/project/shield-cooldown-hud) | 自身盾牌冷却显示，可选对手估计值 | 对手冷却仅为估计；与盾牌状态提示分开，避免重复 HUD。 |
| [HitColor](https://modrinth.com/mod/hitcolor/versions) | 受击颜色与透明度 | 只选这一项，不再加入同功能 PvP 客户端；与光影和实体渲染联合检查。 |
| [Better Hitreg](https://modrinth.com/mod/betterhitreg) | 提前呈现本地命中音画反馈，另有 PvP 辅助显示 | 作者说明它只改变客户端显示和声音，不改变服务器实际命中、网络延迟或伤害。其碰撞箱与准星叠层不重复启用；1.21.11 有 Fabric 发行文件，服务器规则需单独确认。 |

## 集成约束与验收

- 指定项目保留其独有功能；去重针对相同功能的多份实现，不因功能局部重叠删除整项。优先在模组配置中禁用重复部分，若无法独立控制则记录实际限制并选择一个生效来源。
- 只使用作者公开发布、匹配游戏版本及加载器的文件；核实每个文件的依赖、许可证和整合包分发条件。公开下载不自动代表允许重新打包。没有兼容文件时标明未支持，不伪称已有适配。
- 在客户端模组列表中提供真实的安装状态和功能入口，检查独立配置与 Unbox 设置、快捷键及 HUD 布局的冲突。资源包按资源包机制处理。
- 使用独立环境逐项检查启动、启停、设置持久化，以及与现有 HUD、Iris、Sodium、其他渲染模组的兼容性；记录 FPS 和帧时间，不能凭项目描述断言性能收益。
- 服务器可能限制碰撞箱提示、快捷换装、飞行辅助或命中反馈；以目标服务器规则为准。不要将模组作者的兼容声明视为所有服务器允许。
- 后续视角移动体验由用户测试；不新增鼠标平滑或滤波。不要把模糊效果当成视角问题的修复。
