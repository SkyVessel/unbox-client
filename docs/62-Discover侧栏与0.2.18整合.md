# Discover 侧栏与 0.2.18 整合

2026-10-21。开发分支 `codex/release-0.2.18`。

- 左侧 Skins 下增加 Discover，使用现有灰阶、蓝色选中态和图标尺寸。
- 可独立浏览全部版本；选择自己的 Profile 后锁定对应环境。无 Profile 时可浏览，安装前提示先在 Home 创建。
- Modrinth 分类按照 API 返回的分组显示，可多选、清空，按全部选中条件交集筛选，保留搜索、排序和分页。
- Mods / Resource packs / Shaders 共用安装流程，必需依赖、校验、取消和失败回滚沿用 docs/61；好友 Profile 后端和前端均只读。
- 合并 `342636a` 联机分支，保留墓碑挖掘 Mixin 与新增原版多人列表 Mixin。
- 整合检查补齐 VoxLink 启动器第三方声明与对应源码包，修复发布预检缺少源码归档的问题。

## 验证与范围

- TypeScript/Vite 通过；Rust 79 项通过，1 项显式联网测试默认跳过（实际安装验证见 docs/61）。
- Node 67 项通过；新增 Discover 测试首次因测试工具对 option disabled 的判断失败，改为检查 DOM disabled 属性后通过。合计 68 项覆盖。
- 独立 VoxLink TCP/UDP、丢包/乱序、错误密钥、超时和静默代理测试通过；15 项服务端测试通过。
- 六套 26.1 / 26.1.2 / 26.3 × Fabric / NeoForge 构建通过，源码和资源预检通过。
- 本轮只进行无界面测试与构建，没有启动游戏、显示浏览器或操作桌面焦点。联机分支的先前游戏测试证据见 docs/60-VoxLink。

当前 Wrangler 未登录；随后找到已有 Cloudflare MCP 连接，但启动时 OAuth 刷新被上游以 invalid_scope 拒绝，需要用户重新登录。因此新 Worker 尚未部署。旧 Worker 不接受网络能力字段，客户端继续走 LAN / e4mc 中继，不宣称本次已上线公网 P2P。异地穿透率与 Windows 游戏运行尚未实测。
