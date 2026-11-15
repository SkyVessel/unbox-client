# unpvp Windows / Hypixel 大厅性能调查

日期：2026-11-14。分支：`codex/unpvp-ui`。本轮为代码、已安装依赖字节码和公开资料调查，没有修改游戏画质、模组组合或用户配置，没有产生新的 Windows 实测成绩。

## 用户反馈与当前判断

用户已在 Windows 测试各版本：1.8.9 在 Hypixel 大厅与对照客户端相差两倍多，对局内差距较小；高版本没有同样的问题。用户随后确认对照为 Lunar，同一台电脑、网络和可对应设置；双方独有设置尚未逐项核对。双方 FPS 原始采样、具体硬件及大厅编号仍未取得。同名选项不一定意味着相同的实际绘制范围。

这说明此前的 Mac/Rosetta 问题不足以解释当前差距。优先假设是 **1.8.9 密集实体、名字/悬浮文字及 HUD 绘制的优化缺口，叠加双方设置差异**。这是有代码和既有实验支持的方向，尚未定位为 Windows 上唯一或已量化的根因。

## 已确认的代码事实

| 检查点 | 事实与含义 |
| --- | --- |
| 区块渲染 | 已安装 Nothirium，它替换区块渲染器，不能据此认定玩家、悬浮文字、HUD 都获得同等优化 |
| Patcher 子集 | 已有模型批处理等 9 个源文件；没有完整 Patcher 的字体缓存/字体渲染优化、HUD 缓存、实体距离控制。增加一个模组名称不等于装齐它的全部优化 |
| 名字渲染 | 仍使用原字体绘制；Logo 增加局部字形支持，没有把整个名字渲染改为高性能缓存方案 |
| HUD | `LiveHud`、`ExtraHud` 在每帧覆盖层事件中绘制。UI 菜单静态图层缓存不等于游戏 HUD 缓存 |
| 模型遮挡 | `ModelOcclusion` 只跳过完全被挡住的生物模型，仍画名字；排除盔甲架。相机/投影变化时失效并停用当前帧，以避免错误消失。移动观察、开阔大厅和大量悬浮文字时收益有限 |
| 原 EntityCulling | 本机测试实例默认保留玩家及带名字实体。不能简单关闭此保护，否则可能连名字一起不绘制；Windows 实际配置仍需核对 |
| 新增 Logo / 皮肤 | 皮肤磁盘读取在后台；Logo 纹理有缓存。没有发现每个远端玩家每帧下载/解码皮肤的路径。Logo 身份查询仍有重复字符串处理，可作为次级优化，不足以直接归因为两倍差距 |
| 高版本 | 现代版性能锁文件含 Sodium、ImmediatelyFast 等，渲染优化组合与 1.8.9 不同。不能以现代版正常推断 Java 8 客户端没有渲染瓶颈 |

对应代码：`client-mod/src/unpvp/java/dev/unbox/unpvp/{ModelOcclusion,LiveHud,ExtraHud,NameBadges,LocalSkin}.java`，`ports/patcher-performance/src/main/resources/mixins.unbox-patcher.json`，`src-tauri/resources/*performance*.json`。

已有 Mac 隔离实验中，64 个玩家及 48 条悬浮文字会显著降低帧率；诊断性隐藏名字/HUD 后明显恢复，见 [99](99-unpvp服务器大厅帧率优化.md)。此前字体批处理/几何缓存实验在 Mac 上没有稳定收益，已经撤除；不能将它描述为已集成，也不能据 Mac 负结果断言 Windows 必定无效。实验同时改变多个显示因素，不能用它精确拆分模型、文字与 HUD 各自耗时。

## 官方公开资料提供的对照

### Lunar 的旧版专项优化

[Lunar 官方性能说明](https://support.lunarclient.com/solutions/how-to-fix-fps-issues-in-lunar-client) 列出 1.8 专用 Turbo Nametags、旧版实体和方块实体显示距离，以及 HUD Caching。它建议降低实体距离以减轻拥挤服务器开销，并说明 OptiFine 下 HUD 缓存需要关闭 Fast Render。

因此，即使双方 Minecraft 版本、材质包、区块视距相同，仍可能绘制不同数量的玩家、头颅、箱子等。若用户对照的是 Lunar，应额外核对这些选项；这里没有假定用户已经开启最低实体距离。官网说明没有公开足以直接复制 Turbo Nametags 的实现，本轮不宣称已找到该功能的同源码公开 JAR。

### 可研究的公开实现

[Patcher 官方功能清单](https://github.com/Sk1erLLC/Patcher/blob/master/README.md) 列出字体优化/缓存、实体距离、保留名字的遮挡剔除、模型批处理等。我们只接入其中子集。后续可研究其文字和 HUD 路径并在 Windows 对照，但要继续遵守来源许可及现有 UI 约束，不能直接叠加完整客户端运行时。

[Nothirium 1.8.9 作者页面](https://modrinth.com/mod/nothirium-1.8.9-port) 明确其主要作用是更换区块渲染引擎。装入它不等于已有 Sodium 或 Lunar 全套实体/文字优化。

## 视频设置核查

以下“我们的值”来自启动器新实例默认配置，不代表用户 Windows 当前文件。`default_file` 仅创建缺失文件，已有 `optionsof.txt` 会保留；不能只检查仓库默认值就排除配置差异。

| 项目 | 当前默认 / 已确认行为 | 如何判断 |
| --- | --- | --- |
| VSync / FPS 上限 | 默认 VSync 关，`maxFps:260` | 1.8.9 的 260 是滑块最大值，对应 Unlimited，**不是强制锁 260 FPS**。已检查原版字节码的最大值与限帧条件；实际 Windows 配置及驱动限帧仍待核对 |
| Use VBOs | 新实例开 | 实际配置若关，先纠正后对照 |
| 区块视距 | 新实例 8 | 双方相同，并等待区块加载稳定；另行核对实体距离 |
| Smooth FPS | 默认关 | 不宜按名称理解为免费“更流畅”；OptiFine 说明它通过刷新驱动缓冲工作，依赖驱动表现 |
| Smooth World | 默认关 | OptiFine 随包提示明确只针对本地单人世界，不能指望它解决 Hypixel 大厅 |
| Fast Render | 默认关 | 官方资料指出会影响 Motion Blur/Menu Blur 等着色器功能；保持 UI/画质的约束下，不直接强制开启 |
| Render Regions | 默认关，Windows/Mac 共用此初始值 | 独显 Windows 可作为独立对照项，但我们使用 Nothirium，必须确认实际渲染路径是否受此开关影响；不能套用纯 OptiFine 的收益承诺 |
| Fast Math / Chunk Updates / Dynamic Updates | 开 / 1 / 关 | 已与常见性能建议一致，不是遗漏所有优化选项 |
| 抗锯齿 / 各向异性过滤 | `ofAaLevel:0` / `ofAfLevel:1` | 默认没有额外开启高倍 AA/AF；保留 mipmap 4，避免重新引入草木远处闪烁 |
| 光影 | 待核对实际配置 | 当前安装 Nothirium 字节码在 OptiFine 光影启用时选择 GL15 路径，新增模型剔除也会停用；属于重要的配置分支 |
| GPU / 分辨率 | Windows 实际值未知 | 1.8.9 Java 8 与现代版 Java 是不同程序路径，需用双方 F3 确认实际 GPU、物理输出尺寸；现代版使用独显不能证明旧版也使用独显 |

OptiFine 原理参考 [官方设置说明](https://github.com/sp614x/optifine/blob/master/OptiFineDoc/assets/minecraft/optifine/lang/en_us.lang)。本轮同时检查了实际安装 M5 JAR 内 `assets/minecraft/optifine/lang/en_US.lang`，确认 Smooth World、Smooth FPS、Fast Render、Render Regions 的旧版提示，避免只套用新版文案。

## 最小 Windows 定位实验

所有实验同一个大厅编号、位置、朝向、分辨率、材质包和视距，等待加载稳定；每项记录约 20 秒的范围，做完恢复原设置。不要同时改多项。

1. **公平基线**：记录双方 F3 的 GPU/分辨率、FPS，核对 VSync、光影、实体距离、HUD 缓存和名字优化状态。若比较 Lunar，可先把实体距离调至相同范围；Turbo Nametags / HUD Caching 单独切换观察差值，这能衡量专项优化的贡献。
2. **玩家密度**：使用服务器提供的大厅玩家可见性选项临时隐藏其他玩家。若差距明显收窄，优先检查玩家模型、皮肤层、装备和名字；它同时影响模型与名字，不能直接把结果全归为模型。
3. **HUD 与名字**：恢复玩家后按 F1，对比恢复前后。F1 同时影响 HUD 和名字，所以只是定位大类；后续需要分开计时或单独诊断开关，不能单凭 F1 就断言某一个模块的问题。
4. **像素/驱动负载**：保持场景，临时降低窗口物理尺寸。帧率明显跟随像素数变化，支持 GPU/后处理瓶颈；变化不大且人多才掉帧，支持 CPU 绘制提交/实体/文字方向，但仍不是绝对证明。
5. **重复现象**：原尺寸下分别静止观察与持续转视角，比较新增遮挡剔除可能失效的场景；收集日志确认六项性能组件实际加载以及是否有重复异常。

如果要提供文件，应从 Windows 实际 `unpvp-ui` 目录取 `options.txt`、`optionsof.txt`、`config/entityculling.cfg`、`config/nothirium.cfg` 与相关启动日志；无需账号文件、邀请凭证或整个存档。对外分享日志前检查账号/地址等私人内容。

## 后续改动优先级

1. 有 Windows 对照后，优先研究 **保持名字、Logo 和画面一致的文字绘制及 HUD 缓存**；对动态文字、计时、输入反馈和资源包重载做失效检查。
2. 对模型查询建立实际命中率及成本统计，避免无墙大厅不断付出查询成本却没有收益；转视角期间的正确性仍优先。
3. 根据实际渲染路径验证 Windows 专用设置组合，重点检查光影导致的回退和独显选择。
4. 实体距离、隐藏宠物/装饰等会改变可见内容，应作为用户可选取舍，不能默认用隐藏玩家/名字冒充同画质优化。

本轮确认了优化覆盖缺口及设置核查方向，**没有证明某一个视频选项就是两倍差距的根因，没有宣称已经修复，也没有改动当前画质与 UI**。缺少 Windows 配置与实际采样时，不继续凭 Mac 结果增加性能模组。

## 后续反馈：Fullbright、Hit Color、击退及延迟显示

用户进一步报告正式对局中攻击同一对手，Unbox 击退表现比 Lunar 差；HUD 延迟从大厅约 189 ms，入局变为 1 ms，退出约 210 ms。已确认 Toggle Sprint 开启；Shaders 状态未知；是否实际扣血仍待确认。

- Fullbright：当前 unpvp 没有此功能。Lunar 将它纳入 Lighting（[官方更新记录](https://www.lunarclient.com/news/patch-notes-19)、[官方 Lighting 配置](https://lunarclient.dev/apollo/developers/mods/lighting)）。这是亮度功能；没有证据证明缺少它造成大厅两倍帧率差距。本轮未新增该功能。
- 原 Ping HUD 直接读取玩家列表 `NetworkPlayerInfo.getResponseTime()`；服务器报告 1 就显示 1，不是客户端实测的往返时间。不能据此推断入局后网络延迟真实降至 1 ms，也不能直接将数字异常归因为没有击退。
- 已复现并修改 Hit Color 的 OptiFine 光影分支、未启用切换意图时 Toggle Sprint 反复取消原版疾跑的问题；另改 HUD 为低频连接响应估计。修复和验证边界见 [104：对战与延迟排查](104-unpvp对战与延迟排查.md)。这些不是大厅 FPS 根因已解决的证据。
- 普通公共服务器连接沿用原版服务器连接路径，没有通过好友邀请的 VoxLink/e4mc 中继；未发现 Unbox 普通运行代码取消攻击请求、拦截击退包或缩放击退速度。
