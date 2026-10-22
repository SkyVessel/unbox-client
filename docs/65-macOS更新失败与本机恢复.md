# macOS 更新失败与本机恢复（2026-10-22）

## 现场证据

- 用户运行的是项目 `src-tauri/target/debug/bundle/macos/Unbox Client.app`，Info.plist 版本为 0.2.15，位于用户 Documents 目录。
- 系统日志 12:24:35：`dev.unbox.client` 请求 `kTCCServiceSystemPolicyDocumentsFolder`，结果 `authValue=0, authReason=2`，文稿访问被拒绝。
- 系统日志 12:24:39：同一进程的 `system.privilege.admin` 授权成功。因此输入管理员密码成功，仍未解决文稿目录的隐私访问限制。
- 更早的进程还出现签名身份 cdhash 不匹配和旧文稿访问授权不匹配；不能据此断言所有测试者都会遇到同样的问题。当前磁盘上的旧应用通过 codesign 检查。
- 现有 `updates.rs` 将所有 `update.install` 错误替换成同一条权限提示，未保留原始安装错误，无法从该弹窗确定底层错误。以上系统日志直接证实本机存在目录访问限制，未恢复被旧代码丢弃的底层异常。

## 本机恢复

- 从此前匿名下载的 0.2.18 公开发布包重新核对 SHA-256，并用项目既有公钥验证 updater 签名；篡改样本被拒绝。
- 解压后检查版本与 codesign，再安装至 `~/Applications/Unbox Client.app`。安装后再次 codesign 检查通过。
- 没有启动或退出应用，没有修改旧的构建目录应用、账号、Profile 或世界。新旧应用的 bundle identifier 均为 `dev.unbox.client`，使用同一 Application Support 数据目录。
- 用户需退出旧窗口后，打开用户 Applications 中的 0.2.18。旧 Dock 快捷方式若仍指向构建目录，需要改用新位置。
- 尚未从新位置执行真实的下一次应用内更新；不将本机安装和验签视为未来升级流程已完成实测。

## 代码修正与验证

- 安装失败保留实际错误；macOS 权限拒绝提示迁移至 Applications，并解释管理员授权与受保护目录访问的区别。
- 磁盘等其他错误不再误报为权限问题；恢复失败时保留 updater 返回的备份位置。
- `cargo test --manifest-path src-tauri/Cargo.toml updates::tests --locked --offline`：4 项通过，包含原有更新与游戏启动互斥检查。
- 修正仅在本地源码分支 `codex/fix-macos-update-diagnostics`，没有发布新版本；安装的 0.2.18 是原公开发布产物，尚不包含本次提示改动。
