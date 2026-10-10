# Unbox Microsoft 应用接入

2026-10-10。本轮只更新源码与本地 macOS 调试包，没有发布 Release，也没有修改云端。

## 配置与交互

固定公共应用 ID：`0136aeab-bc7a-41b1-988e-08bf665fd53c`。调试与发布构建使用同一 ID，不读取旧 `auth-config.json` 的应用选择，不需要 Tenant ID 或 Client Secret。公共客户端设备码流程继续使用 consumers 端点与 `XboxLive.signin offline_access` scope。

账号界面移除 Application setup、ID 输入和 DevLogin 入口；点击 **Sign in with Microsoft** 后申请设备码、显示倒计时并打开微软官方页面，保留复制代码、重新打开网页与取消功能。授权必须由用户在官方网页完成。启动器不收集微软密码。

旧应用账号在界面显示 Sign in again；点击它直接发起新的 Unbox 授权，不能用旧令牌启动游戏、续期或通过正版好友验证。取消或失败不删除旧账号；新授权通过 Xbox、Minecraft 认证、Java 权益及 Profile 检查后，按 Minecraft UUID 替换同一玩家的旧应用凭证，其他账号保留。UUID 不因更换 OAuth 应用而改变。

令牌继续由 Rust 保存至 macOS Keychain / Windows 受保护存储；React 只接收公开账号与皮肤数据。续期使用保存凭证所属的 Unbox ID，保留刷新令牌轮换、身份一致性验证及过期重登录处理。

## 验证结果和边界

- 微软真实 devicecode 接口：HTTP 200，返回有效设备码结构，900 秒有效期及 `https://www.microsoft.com/link`。预检未完成用户授权，未保存或输出设备码。
- Rust：32 项通过。包括忽略旧应用配置、拒用旧应用凭证、同 UUID 授权迁移、序列化恢复、模拟 Microsoft→Xbox→XSTS→Minecraft→权益→Profile 的令牌链和刷新轮换。
- 启动器：37 项通过。包括自动打开授权网页、无应用配置入口、旧账号重新授权、取消保留账号、皮肤显示和令牌不进入前端状态。
- TypeScript / Vite 构建通过；本地 0.2.5 macOS 调试应用重新打包完成，ad-hoc 签名校验通过，未发布到 GitHub。
- 没有把模拟接口测试或旧 DevLogin 成功记录当成新应用的真实账号测试。Minecraft Services 应用是否获准、真实 Java 权益、玩家 UUID/名称/皮肤、实际启动以及退出重开后续期，需要用户完成新授权后继续验证。

本地日志：`.cache/unbox-auth-device-preflight.json`、`.cache/unbox-auth-rust.log`、`.cache/unbox-auth-node.log`、`.cache/unbox-auth-desktop.log`。不记录登录令牌、设备密钥或用户密码。

## 用户验证步骤

1. 退出旧 Unbox，打开本次构建的 `src-tauri/target/debug/bundle/macos/Unbox Client.app`。
2. 账号菜单 → Sign in with Microsoft，在微软页面输入界面显示的设备码并完成授权；如果钥匙串弹窗出现，在系统提示中允许访问。
3. 确认名称、皮肤与 UUID 对应本人账号，实际启动 26.1 游戏。
4. 关闭游戏与 Unbox，重新打开，确认账号恢复并能再次启动。短时间重启只验证缓存恢复；到期后的真实令牌续期需单独观察，不能等同于短时间重启成功。

微软官方依据：[启用公共客户端流程](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-configuration#enable-public-client-flow)。
