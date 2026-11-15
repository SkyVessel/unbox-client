# unpvp Apple Silicon 与模型遮挡优化

日期：2026-11-14。分支：`codex/unpvp-ui`。范围：本地 Mac 测试构建；没有发布线上版本。

## 用户要求与结果范围

用户要求先实施 Apple Silicon 原生运行、保留名字的模型遮挡剔除，保持画质和 UI。此前用户在 Mac 的 Hypixel 大厅报告约 89 FPS，记分板异常修复没有明显改善。

本轮保留视距、Retina 物理输出、材质过滤、UI 设计、模糊及已有六项性能组件。性能数据来自固定本地模拟大厅，不代表已经在 Hypixel 实测同样增幅。Windows/Linux 未实玩验证。

## 原生运行时

`src-tauri/resources/unpvp-apple-silicon.json` 单独固定 ARM64 Java 与 LWJGL。本机启动器在 macOS/aarch64 编译时选用：

- Azul Zulu Java 8u512 ARM64，目录 `java8-osx-arm64-8u512`，归档 SHA-256 `54758f5d72e8832c4862ec7d9b53be24616048db42ca325cdf7d29a20bb63132`。
- Mojang LWJGL / lwjgl_util `2.9.4-nightly-20150209`。
- MinecraftMachina `2.9.4-20150209-mmachina.1` macOS 本地库，SHA-256 `076f9392f8597cd8bf68b1620a60638927ad55d49670523f1905a39ed6193fc8`。
- 独立目录 `natives-osx-arm64-mmachina1-retina`，避免与 Intel 本地库混装。其他平台保留原清单。

Java 归档先校验 SHA-256，库文件按 SHA-1 校验并保留既有缓存凭据机制。启动器已缓存准备测试两次分别为 288 ms、18 ms；这是资源准备时间，不是从点击到标题画面的完整启动时间。

来源：[Azul 固定归档](https://cdn.azul.com/zulu/bin/zulu8.98.0.111-ca-jre8.0.512-macosx_aarch64.tar.gz)、[MinecraftMachina 固定发布](https://github.com/MinecraftMachina/lwjgl/releases/tag/2.9.4-20150209-mmachina.1)。

## 标题主菜单异常的真正原因

初次使用 mmachina.2 时，游戏报告 Retina 2 倍尺寸、按 2560×1600 绘制，但该库强制 `setWantsBestResolutionOpenGLSurface:NO`，真实窗口表面仍是 1280×800。最终呈现被裁剪，用户看到放大且偏到角落的主菜单。

mmachina.1 保留 HighDPI 表面，解决窗口与游戏尺寸不一致。没有缩小 UI 来掩盖问题，也没有关掉 Retina 来换帧率。用户已确认标题 Logo、Singleplayer、Multiplayer、底部按钮完整且大小正常。

新增 `PresentationProbe` 在交换窗口缓冲前，比对游戏帧缓冲与真实窗口后缓冲；2560×1600 下 62,964 个采样点无差异。普通游戏内部截图不足以检出该错误。初次 mmachina.2 的性能结果不作为最终架构对比。

## 保留名字的遮挡剔除

核心实现 `ModelOcclusion.java`，挂接点为 `ModelOcclusionMixin`、`ModelNamesAccess`、`ModelCullingOwnerMixin`。基于 Patcher 的公开 GL15 查询和保留名称做法进行适配，固定提交、许可证与改动说明见 `ports/pvp-independent/UPSTREAM.json`。核心源码随核心 JAR，Patcher 许可证随 PvP JAR；没有引入 OneConfig 或完整 Patcher 客户端。

绘制前对扩展包围盒异步查询；只读取已准备好的查询结果。完全被挡住时跳过模型，并调用原版名称渲染逻辑，所以队伍、潜行和原版名称可见性仍生效。盔甲架/悬浮文字不进入新增路径。通过 RenderLib 挂接避免另一套剔除先隐藏整个实体及名字。

保守边界：相机位置、方向、实际投影/视图矩阵变化时使旧结果失效，涵盖缩放缓动与视角晃动；实体移动、区块更新、过期或未返回的查询优先正常绘制。最多缓存 512 个实体，每帧最多发出 64 个查询；离开世界清理。启用光影、旁观模式、受伤晃动时停用新增剔除。因此收益偏向停留观察且存在遮挡的场景，不能承诺持续转视角时同样增幅。

## 同画质架构对比

M3 Max，逻辑窗口 1280×800，真实输出 2560×1600；视距 8、mipmap 4、VBO，关闭 VSync，保留相同模组。64 个客户端测试玩家及 48 个悬浮文字；每场景预热 8 秒、采样 8 秒。两组都关闭新增模型剔除，以隔离架构影响。

| 密集玩家/文字场景 | 平均 FPS | P95 帧时间 | P99 帧时间 |
| --- | ---: | ---: | ---: |
| Intel Java / Rosetta | 139.031 | 10.162 ms | 14.932 ms |
| ARM64 / 正确 Retina 输出 | 211.471 | 7.589 ms | 10.099 ms |

平均约 +52%。这是短时固定场景对照，未提供长期统计置信区间；实际大厅人数、朝向、文字与粒子会影响结果。原始数据：`.cache/unpvp-tests/native-intel-baseline/lobby-probe/frames.csv`、`native-arm-retina-baseline/lobby-probe/frames.csv`。

## 验证记录

- Rust 运行时/缓存单元检查 4 项通过；真实启动器缓存准备检查通过。
- `native-presentation-fixed`：实际窗口呈现像素对照通过，退出 0。
- `native-pvp-fixed`：97 项 PvP/UI 功能检查通过，退出 0。
- `native-resize`：31 项 GUI 缩放、窗口尺寸和全屏切换检查通过，退出 0。使用 `--display-only`，没有将未聚焦窗口的自动点击失败算作输入通过；标题视觉另有用户确认。
- `native-occlusion-names`：可见前排模型未误剔除；墙后模型被跳过且调用名称；开启/关闭剔除的墙和名字像素一致；诊断隐藏名字后像素确实不同；墙移除后模型恢复；清理后查询为空；退出 0。
- 上述遮挡测试：关闭约 305.565 FPS，开启约 374.877 FPS（约 +23%）。该增幅与架构测试来自不同场景，不能直接相乘。隐藏名字的诊断高帧率不属于产品收益。
- 早期测试误用了隐藏 HUD 设置，连玩家名字也隐藏，已废弃其收益数据。最初移除墙后立即断言失败：Nothirium 异步网格尚未移除旧墙，约几帧仍视作遮挡；最终检查在网格预热后验证完整采样阶段没有残留剔除。

### 最终遮挡回归

`native-occlusion-final` 通过名称像素一致、实际相机矩阵变化使缓存失效、可见模型、移除墙和清理检查，退出 0；无 GL 错误。最终墙后场景关闭 310.222 FPS，开启 391.682 FPS（约 +26%）；P95 5.832→5.233 ms，P99 6.257→5.889 ms。

可见且没有墙的场景关闭 232.844、开启 220.304 FPS。新增查询有开销，收益取决于遮挡比例，不能声称所有场景都提高帧率。前一轮相同类型可见场景反而开启较高，也说明短采样存在波动。最终数据保留在 `.cache/unpvp-tests/native-occlusion-final/lobby-probe/frames.csv`。


## 本地应用交付

`npm run tauri -- build --debug --bundles app` 成功。产物为 `src-tauri/target/debug/bundle/macos/Unbox Client.app`。应用包内核心、PvP、Patcher JAR 和 ARM 清单与构建资源逐字节一致；许可证、上游出处、模型剔除源码存在。审计记录：`.cache/unpvp-native/bundle-audit.json`。

已将通过哈希验证的 ARM Java 归档及三个库文件预置到现有启动器缓存；由启动器负责提取与选择运行时，不覆盖个人 options、材质包、世界或按键配置。原 Intel 缓存保留。

实际交付启动：退出旧启动器，打开新调试包，在 UI 选择原有 `Wefwef · Forge 1.8.9` 并点击 Launch。启动器显示 `PLAYING`；Profile 中核心/PvP JAR 哈希与新包一致。运行日志确认 Java `1.8.0_512 / aarch64`、LWJGL `2.9.4`、逻辑窗口 `1280×800` / 物理帧缓冲 `2560×1600`，首个 Unbox 标题帧在 JVM 启动后 `11302 ms`。声音引擎启动，无 Mixin apply/FATAL 错误，游戏进程保留供用户测试。

启动器自身重开曾停在 `Opening Unbox Client` 一段时间后恢复；未定位该等待的根因，没有据此修改账号系统。它不包含在上述 JVM 到标题的 11.3 秒数据中。未自动连接 Hypixel；实际大厅帧率、移动操作和长时间稳定性留给用户复测。
