# OneClient 设置对照与 Shift 升级

2026-10-06。范围：Unbox 26.1 的实际 Fabric 模组、桌面账号授权与皮肤显示。界面保持英文。本轮读取的是上游源码与官方资料，没有宣称安装并实际操作过竞争客户端。

## 调研依据

本轮没有停留在 OneConfig 框架截图，而是读取了对应模块的配置及实现：

| 上游原始来源（锁定提交） | 核实到的交互／设置 | 本轮 Unbox 对应实现 |
| --- | --- | --- |
| [PolyCrosshair / ModConfig](https://github.com/Polyfrost/PolyCrosshair/blob/9d424cff2a5504cd8718469b605a60e8704cfe87/src/main/kotlin/org/polyfrost/crosshair/config/ModConfig.kt) | 自定义画布、镜像、旋转、缩放、按目标改色、显示条件、依赖项控制 | General / Behavior / Canvas；15×15 单色可拖绘画布、镜像、清空与分享码；旋转、缩放、实体目标颜色、第三人称／F3／旁观模式开关 |
| [OverflowParticles / ParticleConfig](https://github.com/Polyfrost/OverflowParticles/blob/ce535f688de87609ab50767531a98e4c2c1dd254/src/main/kotlin/org/polyfrost/overflowparticles/client/config/ParticleConfig.kt) | 每种粒子的开关、大小、颜色、倍数、消退等，而非只有一个总开关 | General 总密度／常用分类；Types 八类粒子分别设置可见性、密度、大小、寿命、颜色；示意预览；设置实际作用于新产生的粒子 |
| [EvergreenHUD / FpsHud](https://github.com/Polyfrost/EvergreenHUD/blob/1e226fd523b45fa2194777d5b6974b9175c13f3a/src/main/kotlin/org/polyfrost/evergreenhud/client/hud/FpsHud.kt) | 内容格式、刷新周期、显示条件 | FPS 标签／刷新间隔；按用户要求始终无背景、无边框；独立大小、颜色、阴影、位置锁定、聊天／F3 显示条件 |
| [EvergreenHUD / CpsHud](https://github.com/Polyfrost/EvergreenHUD/blob/1e226fd523b45fa2194777d5b6974b9175c13f3a/src/main/kotlin/org/polyfrost/evergreenhud/client/hud/CpsHud.kt) | 左／右／双键模式、1 秒滑动窗口、输入事件计数 | 保留 1 秒窗口；按用户要求用 LCPS / RCPS 直角白框；真实鼠标按下立即亮起，按住保持、释放淡出；亮色／透明度／时间可调 |
| [EvergreenHUD / ArmorHud](https://github.com/Polyfrost/EvergreenHUD/blob/1e226fd523b45fa2194777d5b6974b9175c13f3a/src/main/kotlin/org/polyfrost/evergreenhud/client/hud/ArmorHud.kt) | 六装备槽、横／竖排列、反向、耐久格式与颜色 | 独立装备槽、主／副手、排列与顺序、数量／百分比／两者、耐久条、动态颜色、低耐久警告；真实游戏物品图标 |
| [Chatting / ChattingConfig](https://github.com/Polyfrost/Chatting/blob/9347abe040c0fefb77a8b22a08fee8127c47e32d/src/main/kotlin/org/polyfrost/chatting/config/ChattingConfig.kt) | 背景与文本、展开／收起高度、聊天布局 | 背景颜色／透明度，文本透明度、宽度、字号、最大行数、展开／收起高度、行距；关闭模块恢复此前原版值 |
| [PolySprint](https://github.com/Polyfrost/PolySprint/blob/901e571e366cc5c224d91ec3daf7862d61b2b931/src/main/kotlin/org/polyfrost/polysprint/client/PolySprintConfig.kt) | 切换疾跑与下蹲、独立按键、显示条件 | 明确 Unbox 现有行为是 Auto Sprint；修正启动器中的误名。保留原版饥饿／碰撞限制；没有伪装为已实现上游所有控制 |

研究快照保存在 `research/shift-panel/`，不参与打包。上述实现为按行为重新实现，没有复制其 GPL/LGPL 源码或品牌素材。

## 渲染与操作

- 用过滤后的高分辨率圆形遮罩绘制圆角，每个圆角面板固定四个角加三个矩形，不再逐 GUI 整数行拼圆角。
- Barlow 字体生成 8 倍分辨率字形图集，保留亚像素布局和纹理过滤；菜单英文标签使用此绘制路径。常驻 HUD 则把同款平滑字形的整段文字合成一个 GuiElementRenderState 提交；布局缓存有 256 项上限，避免逐字符创建渲染状态和矩阵副本。非图集字符回退原版字体。图标也启用纹理过滤。
- 悬停与开关采用基于时间的指数过渡；重建设置行时保留开关动画状态。
- 保留游戏自带一次背景模糊与半透明面板，避免重复调用模糊引发崩溃。没有引入浏览器或 Compose/Skia 运行时。
- 右 Shift 的 Mods 主面板可直接拖动露在浮动面板外的已启用 HUD；紧凑尺寸下为 HUD 留出左侧空间。拖动经过面板时 HUD 会提升到前景，松手后面板控件优先；如把 HUD 放到面板下面，可用 Edit HUD 继续调整。进入单个模组设置时使用右侧预览，避免真实 HUD 覆盖控件。Edit HUD 保留缩放、吸附、键盘微调、取消与恢复。
- HUD 的基础尺寸不再随原版 GUI Scale 2→4→6 等比放大；自身 Scale 控制大小。拖动坐标转换保持一致。
- FPS 永远无背景；CPS 永远保持直角白边且无静态填色。减少无效外观选项。位置锁定同时约束直接拖动与编辑模式。
- 盔甲预览物品延迟创建并缓存；不在注册表初始化前构造 ItemStack，也不每帧重新创建。

## 取舍和边界

- 粒子类型目前通过缓存的粒子实现类族识别八类；不是 OverflowParticles 的完整注册表级逐粒子编辑。未匹配类型仍受总密度控制。部分原版粒子有专用缩放／寿命曲线，最终效果受原版实现限制。
- 密度范围限制在 0–100%，没有添加伪暴击、攻击粒子倍增等与本轮性能目标无关的行为。
- 准星画布是单色 15×15。分享码只包含画布，不包含全部颜色／旋转设置；不兼容其他客户端分享码。没有声称已实现上游彩色画布、所有目标类别或颜色反转。
- CPS 统计物理左／右鼠标，而不是键盘重绑定的攻击／使用动作；面板点击不计入游戏 CPS。
- 装备编辑预览使用样例物品；关闭面板后只显示真实装备。
- 尚未实现 OneConfig 的全部 HUD 模板、全部模块选项或其 Compose/Skia 渲染架构。

## 正版登录与皮肤

- 使用 [Microsoft 官方设备授权协议](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code)：桌面显示验证码，用户主动打开 Microsoft 网页授权；支持等待、超时、取消和服务端降速要求。
- OAuth → Xbox User → XSTS → Minecraft Services → Java 权益／档案验证。以官方档案名称与 UUID 启动，不把 Microsoft 账号当成本地离线身份。
- Microsoft 刷新令牌和 Minecraft 访问令牌仅保存在 macOS Keychain；不返回给 React、写入 localStorage、普通配置或 Cloudflare。启动前刷新将要过期的会话。
- 前端只接收名称、UUID、真实皮肤 PNG 与 Classic / Slim 模型；头像从皮肤头部裁切，3D 预览使用 [skinview3d](https://github.com/bs-community/skinview3d)。预览按需绘制，禁止常驻动画循环。
- 皮肤只从 `textures.minecraft.net/texture/` 获取，HTTP 原始链接升级 HTTPS；禁止重定向并限制下载大小。
- 注册应用必须属于 Unbox。没有借用官方／其他启动器 Client ID。
- **当前阻碍**：没有提供 Unbox Client ID 和 Minecraft Services 的应用批准证据，因此没有完成真人账号端到端登录。代码、模拟授权 UI、服务错误处理已经可验证；这不等于实际账号登录成功。
- Wrangler `whoami` 返回未认证。未创建或部署 Cloudflare 资源，未启用付费服务。本地正版登录并不依赖 Cloudflare。

## 应用注册准备

1. 在 Microsoft Entra 创建面向个人 Microsoft 账号的 Unbox 公共客户端应用，保存 Application (client) ID；不需要 Client Secret。
2. 在 Authentication 启用公共客户端流程，让应用可以使用设备授权。
3. 按 Minecraft 的 [App Registration 指引入口](https://aka.ms/AppRegInfo) 申请允许该 Client ID 调用 Minecraft Services。仅有 Entra 注册不保证 Minecraft API 放行；403 会在客户端解释为应用未获批准。
4. 打开 Unbox → Sign in → Application setup，输入该 Client ID；再点击 Sign in with Microsoft。用户密码只输入 Microsoft 网站。

本版本安全凭据存储仅实现 macOS；其他平台返回明确错误，不回退到明文存储。

## 验证记录

第一轮真实游戏测试 47 项；第二轮 60 项，包含新增画布实际点击、分享往返、粒子类型依赖、真实粒子寿命、三个 HUD 的直接拖动及锁定。最终版本另做回归，结果见下方补充及 `IMPLEMENTATION.md`。

桌面侧新增测试覆盖模拟 Microsoft 完成授权后的账号／皮肤展示、取消保留原账号。Rust 测试覆盖皮肤来源限制、Minecraft 应用批准错误、Xbox 档案／家庭限制、无 Java 档案及令牌不进入公开账号响应。**这些测试未调用用户账号，也不是微软生产授权链路的实测。**

最终打包版本 `5d11be0f7f0b5519085db4d97ad2341230dfb17d33b27ecb54664e72cca38593`（JAR SHA-256）通过真实游戏 60 项回归；29 张自动采集截图存于 `design/exports/in-game-v3/`，已检查主面板、CPS 设置、准星画布、粒子分类和大 GUI Scale 设置页。macOS 调试应用重新打包成功。60 项包括页面渲染断言，不等于每个设置的所有组合均已验证。

### 性能结果与未解决项

同机、同世界副本、2560×1440、视距 12、30 秒预热与 45 秒采样，两次新版批量字形结果为平均 **308–311 FPS**，旧版两次为 **222–231 FPS**。但新版 **1% low 91–92 FPS，旧版 109–110 FPS**；p99 从约 8.3 ms 增至 10.4 ms。存在尚未解决的慢帧退步，不能宣称性能全面提高，更不能据此声称超过其他客户端。当前保留新版的交互与平滑绘制，同时把帧时间波动列为后续性能问题。

完整原始数据、各中间方案和条件见 `research/benchmarks/2026-10-06-shift-v3/README.md`。该测试是静态单人场景，不代表移动加载区块、多人联机或大型整合包。没有在缺少证据时继续宣称性能优势。
