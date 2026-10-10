# 0.2.5 · NeoForge 26.1 原生适配

2026-10-09。本轮只生成本地 macOS 调试包，没有发布 Release，也没有更新 Cloudflare。

## 实现方式

同一份 Unbox 功能源码分别编译成 Fabric 与 NeoForge JAR，不把 Fabric 模组装入 NeoForge，不依赖兼容加载器，不做运行时 JAR 转换。Minecraft 仍精确为 26.1，NeoForge 固定 26.1.0.19-beta。

- `client-mod/src/main/java`：共同的主菜单、Right Shift 菜单、Client Mods 设置、HUD、粒子、准星、地图、好友桥接、邀请身份校验、皮肤属性恢复与差异传输。
- `client-mod/src/fabric/java`：Fabric 入口、按键、HUD、登录网络及地图事件注册；继续使用现有 Freelook 依赖。
- `client-mod/src/neoforge/java`：NeoForge 入口、原生事件/按键/HUD、原生地图 payload、登录查询接入、NeoForge 元数据解析和原生 Freelook。
- 资源、图标、字体和设置目录共用。编译时选择加载器源集，不在每帧判断加载器或反射转发绘制调用。
- 两边仍使用各加载器正常支持的 Mixin 挂接 Minecraft 方法；这不是把一种加载器的第三方模组转换成另一种。Sodium 自带设置页仍沿用既有的工厂调用。

NeoForge JAR 构建检查会拒绝 `net/fabricmc/` 或 `freelook/freelook/` 类依赖，避免意外夹带 Fabric 专用实现。

## 功能与设置

NeoForge 现在可用：品牌主菜单、Right Shift 快捷页/Mods/设置、FPS、CPS、按键显示、坐标、盔甲耐久、准星及画布、粒子调整、自动疾跑、缩放、聊天、Freelook、OptiFine 披风显示、地图和延迟 HUD。

地图的地形采样、HUD 拖动/缩放、M 大地图、彩色死亡标记、权限受控传送共用原有逻辑。死亡与网络注册由各加载器完成。NeoForge Freelook 直接改变相机视角，玩家朝向不跟着改变；不新增普通鼠标移动平滑。

启动器在 NeoForge Profile 下开放相同的设置目录。性能模组使用对应的 NeoForge 包：Sodium、Lithium、FerriteCore、ImmediatelyFast、Dynamic FPS；未强装没有精确兼容版本的 EntityCulling。中继使用原生 e4mc NeoForge 6.2.3。

## 私密邀请与模组同步

Cloudflare 继续处理相同的账号、好友、房间、邀请和中继请求协议。游戏端的加载器变化不要求修改这些接口，所以本轮不需要 Cloudflare 插件或重新部署。

NeoForge 登录阶段直接使用 Minecraft 的加密连接与 Unbox 查询协议，在其正式配置握手之前完成邀请证明及玩法模组检查。仅接管 Unbox 自有通道与事务编号，其他查询仍交给原来的处理逻辑。邀请过期、错误密钥及房主身份冒用不会放行。

游戏侧用 NeoForge 自带 TOML 解析器读取元数据；启动器独立校验下载 JAR 的摘要、大小、元数据与模组身份。共享 Profile 根据好友身份和加载器分别命名，不覆盖原 Profile，不把 Fabric JAR 转成 NeoForge JAR。现阶段与朋友联机应选择同一种加载器；跨加载器自动切换不在本次验证范围。

## 已验证

| 验证 | 结果 |
| --- | --- |
| Rust | 31 项通过，包括 NeoForge 共享 Profile、校验错误加载器元数据 |
| Node/浏览器 | 37 项通过，包括 NeoForge 模组开关与完整设置入口 |
| NeoForge 游戏 UI | 116 项通过 |
| Fabric 回归 UI | 116 项通过 |
| NeoForge 同机局域网双客户端 | 正常邀请、同名不同 UUID、房主防冒用、错误邀请拒绝、游戏数据、死亡标记和权限检查通过 |
| Fabric 双客户端回归 | 同样的私密联机路径通过 |
| NeoForge 自动模组同步 | 房主安装测试玩法 JAR；访客经加密连接下载，原生启动器创建独立 NeoForge Profile，重启后重新加入；访客实际注册并加载该模组的新物品 |
| NeoForge 公网中继 | 刻意移除访客可见的 LAN 地址，经真实 e4mc 公网中继连接并进入世界通过 |

UI 检查包含真正向按键映射发送 M 打开地图，以及改变 Freelook 后检查实际渲染相机；没有将直接调用打开方法当作按键通过。

测试世界均为复制件，未使用用户世界原件。联机测试账号来自本地 Cloudflare 兼容测试服务；公网中继确实经过外部 e4mc。它们不代表两个异地家庭网络的稳定性、真实微软双账号或大型整合包全部经过验证。OptiFine 远端披风、微软皮肤下载仍受其服务可用性影响。

本地详细日志：`.cache/neo-native-025-ui.log`、`.cache/fabric-native-025-ui.log`、`.cache/neo-private-native-02.log`、`.cache/neo-private-sync-native-02.log`、`.cache/neo-private-relay-native-01.log`、`.cache/fabric-private-native-01.log`。不公开包含用户好友信息的截图、账号或令牌文件。

## 性能复查

Apple M3 Max、36 GiB 内存，NeoForge 26.1.0.19-beta，相同世界复制件与固定视角；2560×1440、渲染距离 12、模拟距离 8、Java 最大堆 4 GiB。每次预热 30 秒、采样 45 秒，两次顺序运行；Sodium/Lithium/FerriteCore/ImmediatelyFast 启用，前台测试排除 Dynamic FPS。FPS/CPS/坐标/盔甲 HUD 均启用。

| 状态 | 平均 FPS | 1% low FPS | p95 帧耗时 | p99 帧耗时 |
| --- | ---: | ---: | ---: | ---: |
| 小地图及延迟关闭 | 648.465 | 133.235 | 3.726 ms | 4.639 ms |
| 小地图及延迟开启 | 709.520 | 121.043 | 3.518 ms | 4.195 ms |

两次失焦帧均为 0。1% low 是最慢 1% 帧的平均耗时倒数。开启组平均更高、1% low 更低，说明不能用这组单次顺序样本证明地图提升帧率或没有开销；它仅确认该场景能够运行并记录帧时间。没有证明比其他客户端更快，也没有覆盖移动加载地形、大型整合包或长时间游玩。

可复查报告保存在 `research/benchmarks/2026-10-09-neoforge/`，其中 README 说明测试方法和限制。

## 构建与使用

- Fabric：`sh scripts/build-mod.sh`
- NeoForge：`python3 scripts/build-neoforge-mod.py`，需要已准备的官方 NeoForge 26.1 运行时；可用 `UNBOX_NEO_RUNTIME` 指定目录（默认 `.cache/neoforge/runtime`）。
- 桌面：`npm run tauri -- build --debug --bundles app`
- Windows 构建流程已改为同时下载并校验两个预编译模块，但本轮没有运行 Windows 构建，不声称已经测试 Windows。

本地应用：`src-tauri/target/debug/bundle/macos/Unbox Client.app`。版本 0.2.5，两个内置模块与最终构建的 SHA-256 一致；本地 ad-hoc 签名校验通过，不是 Apple 公证发行包。没有自动退出用户正在运行的旧应用。退出旧版、打开本地包、创建 NeoForge 26.1 Profile 后启动；已有 NeoForge Profile 会在启动时补入原生 Unbox 模块。

## 接口依据

[NeoForge 26.1 事件文档](https://docs.neoforged.net/docs/concepts/events/)与[原生 Payload 文档](https://docs.neoforged.net/docs/networking/payload/)，并直接检查固定版本 JAR 的真实方法签名。e4mc 使用 [Modrinth 的 NeoForge 发布包](https://modrinth.com/mod/e4mc/version/6.2.3-neoforge)，校验摘要保存在 `src-tauri/resources/neoforge-performance-lock.json`。
