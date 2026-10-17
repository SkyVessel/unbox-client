# 启动器还原、Freecam 与昵称修复

2026-10-14。此文件修订文档 29 中的启动器界面与 Freecam 实现说明。

## 用户确认的边界

- Profile 是启动配置：玩家继续独立创建、命名、选择 Profile；共用世界及资源库是底层存储行为。
- 恢复原主页 News / Mods / Packs / Shaders、News 卡片、Launch 和 Profile 栏；撤销新增版本/加载器选择器、Worlds 标签及世界预检弹窗。
- 保留此前要求删除启动器 Client Mods 页面；游戏内配置入口继续保留。
- 标题界面按钮恢复原有玻璃底色及细边框；Right Shift 与 Mods 保持已选定的浮动设计。
- 本轮只继续修复 Freecam、Nickname 与关联标志识别，不扩展其他新模组或制作披风/建筑功能。

## 皮肤

Launch 旁人物模型下展示皮肤库头像，固定宽度横向滚动。点击直接应用，应用期间显示进度，失败显示原因。Microsoft、Unbox 与 Local 身份都可管理本机皮肤库；Microsoft 的应用仍使用官方账号皮肤接口，其他身份为 Unbox 本地外观。进入主页或重新聚焦窗口刷新库，保存时保护同期 Profile/账号变更。

## Freecam

独立的客户端 `RemotePlayer` 旁观相机，复用原生 `Camera` 位置/朝向插值，不切换服务器玩家的游戏模式。真实本体留在世界中，其他玩家仍可以看到或影响本体。

- 默认 F6；Mods → Freecam → Keybind 可修改，保存到原生按键配置及 Unbox 配置。
- WASD、Space/Shift、Sprint 控制相机；同键退出，打开界面、死亡、离开世界或禁用时恢复。
- 修复直接覆写相机导致的跳动；同步 `RemotePlayer` 的头部朝向，避免逻辑 yaw 与渲染 yaw 分离。
- 分离相机时隐藏第一人称手部，并只在原版本地玩家过滤处允许本体参加正常实体渲染。
- Freecam 优先于 Freelook 的运行态，不改变用户保存的 Freelook 配置。
- 继续遵守此前普通服务器禁用、单人/LAN/验证私密房间允许的规则。

## 昵称及标志

- 自己的名字在第三人称（F5）与 Freecam 看回本体时显示。第一人称不把自己的名字挡在镜头上；F1 仍隐藏名字。
- Nickname 仅替换本人本地显示，不更改账号身份、聊天发送内容或其他玩家看到的名字。
- 名字先应用昵称再加 Unbox 标志，避免两个渲染钩子相互覆盖。关闭 Nickname 恢复真实名字，标志仍保留。
- LAN/Unbox 房间使用已有 capability roster；定期重发避免重连漏标志。
- 普通服务器使用现有登录账号服务：游戏只把当前玩家列表 UUID 写入本地桥接，启动器沿用 20 秒同步；Worker 返回请求中最近 90 秒处于 Playing/Hosting 的 Microsoft/Unbox UUID。按 UUID 识别，不能按昵称猜测。
- 双方需运行新版、登录账号、保持启动器及网络连接。未登录的 Local 身份不通过云识别；旧客户端没有新的查询逻辑。离线模式服务器本身允许 UUID 冒用，不把标志作为权限或身份认证依据。
- 缓存过期、断开服务或账号不一致时不使用云识别结果；不改变原版对其他玩家隐身/队伍名字可见性的处理。

## 验证与交付记录

- Rust 51/51；前端 UI 21/21；Worker/账号 14/14。
- 主页 1000×720、1280×800、1920×1080 真实浏览器 fixture 截图：`test-results/home-skins-*.png`。
- 游戏测试只使用 `.cache/benchmarks` 下复制的世界；最终截图及运行结果见下方补充，不打开用户原存档。
- 早期测试仅验证控制器状态，未发现真实相机 yaw 与本体过滤问题；现已加入实际渲染相机实体、位置、yaw、pitch 断言并人工检查截图，不将早期通过当作最终视觉成功。
- 现有免费账号 Worker 已更新（未发布新的 GitHub 客户端 Release），部署版本 `5e9004db-140c-46fe-9d8c-5627b8ed7179`，100% 生效。`/health` HTTP 200、configured=true。无数据库 schema 迁移，无新付费服务。
- 普通服务器标志：查询/缓存/渲染分别验证；尚未在两台真实电脑、两个账号的公网服务器会合场景做端到端实玩。

### 最终本机验证

| 版本 / 加载器 | 隔离运行目录 | 结果 |
| --- | --- | --- |
| Fabric 26.1.2 | `.cache/benchmarks/revision-namecam-final` | 48/48 |
| NeoForge 26.1.2 | `.cache/benchmarks/revision-neo-namecam-final` | 48/48 |
| Fabric 26.1 | `.cache/benchmarks/revision-fabric261-final` | 48/48 |
| NeoForge 26.1 | `.cache/benchmarks/revision-neo261-final` | 48/48 |

以上目录的 `screenshots/v4-freecam-body.png` 和 `v4-own-nameplate.png` 保存实际游戏画面；已检查本体、昵称与 Logo。相机移动约 7 格，本体位移 0；实际渲染相机与旁观实体位置/朝向一致；通过设置界面将键位改为 F7 后启动/退出，退出恢复原视角。用户配置仍默认 F6，测试配置仅在隔离目录内。

本地 macOS ARM64 调试包已重建：`src-tauri/target/debug/bundle/macos/Unbox Client.app`，四份内嵌 JAR 与最终源产物逐字节一致，摘要记录在 `.cache/revision-bundle-sha256.json`。不把本机验证称为 Windows 实玩或公网双账号互见实测。

### 2026-10-14 方向与人物预览补修

用户实测发现上轮未覆盖的 A/D 反向、预览拖动反向，以及只有头部跟随鼠标的问题。本轮仅修复这些路径：

- Freecam 右移向量改为 `(-cos(yaw), 0, -sin(yaw))`，与 Minecraft 朝向坐标一致；前进、升降和本体隔离保持原实现。
- 人物预览拖动的旋转增量反向；身体与头部改为原版 InventoryScreen 的 atan 鼠标跟随，增加原版整个人物的俯仰变换。拖动期间保持跟随角度，避免鼠标跟随与拖动相互抵消；松开后继续跟随。
- 只修改提取出的预览渲染状态，不写入真实玩家的朝向。保留不同 GUI 比例下的绝对绘制边界处理。
- 回归增加四个朝向下真实相机 A/D 位移、头部及身体左右/上下跟随、拖动正反向和真实玩家朝向不变的检查；不再用“可调用 rotate”充当方向验证。

本轮四组实际游戏回归均为 **60/60**：Fabric / NeoForge × 26.1 / 26.1.2；运行目录分别为 `.cache/benchmarks/directions-fabric261`、`directions-neo261`、`directions-fabric262`、`directions-neo262`。已检查普通与 GUI 6 预览截图及旋转后画面。本轮本地 macOS 调试应用构建通过，四份内嵌 JAR 与源产物逐字节一致，摘要位于 `.cache/directions-bundle-sha256.json`。没有发布 GitHub 更新。
