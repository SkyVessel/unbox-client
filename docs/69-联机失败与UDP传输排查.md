# 联机失败与 UDP 传输排查

2026-10-23，用户反馈局域网与手机热点均失败，包含模组同步与无需同步的直接加入。此次只检查和静默诊断，没有修改产品代码、用户网络设置或重新发布。

## 已确认

- 0.2.19 构建提交 fd03cb3 包含 codex/voxlink-multiplayer 的 342636a、合并提交 9ea6e82、codex/multiplayer-flow 和后续信令部署 a548a02。本机实际游戏 JAR 为 0.2.19，SHA-256 与发布资源一致。
- 线上 /health 返回 200，configured=true；不带凭证请求 /v1/invites/transport 返回预期 401。本机好友视图 connected=true，transport=voxlink-v1。没有拿用户令牌发测试请求。
- 最近五段尝试全部选择 voxlink_udp，包含一段取消、四段失败；四段失败发生在连接后约 11.8–13.1 秒，都进入了 checking_files，一段继续 syncing_files。日志记录收到对端 RESTART 和 DISCONNECT，原版只显示 Disconnected。
- 最新 environment-sync 记录为 restart=false、modCount=0、downloadedBytes=0。这一段在文件检查通过后仍断开，说明失败不局限于下载模组。
- 当前 Surfshark WireGuard 连接有效，去往房主公网地址的实际路由为 utun4，MTU=1380。当前访客 Wi-Fi 与房主通告 LAN 地址不处于同一 /24 网段；记录实际走 P2P，不能把这些记录称为已走 LAN 直连。没有更改 VPN 或路由。

## 静默对照

使用已发布的 ReliableUdpTransport 类，通过本地 UDP 转发夹具模拟 60 ms RTT。模拟路径丢弃超过 1352 字节的 UDP payload（对应 IPv4 MTU 1380 减去 IP/UDP 头），并分别测试原有写入与 900 字节分段写入。

- 原始传输：小握手成功，128 KB 数据不能传输；约 11.3 秒产生 TX dead: 10 heartbeats no ack progress，随后对端收到与用户日志相同的 RESTART / DISCONNECT。
- 小分段对照：相同路径传输 128 KB 字节完全一致，空闲 13 秒后仍可收发。
- 未限制到 1352 字节的对照通过，说明模拟中差异来自大包丢弃。
- 这是模拟大包黑洞的复现，并非对真实 VPN 报文的抓包证据。实际路径是否丢弃分片、房主触发的具体断开条件，仍需房主日志或关闭 VPN 后的对照验证。

## 代码中确定的问题

1. ReliableUdpTransport 的 MAX_PAYLOAD=1400，DATA 加 13 字节头、4 字节认证和 IPv4/UDP 头共 1445 字节，超过当前隧道 MTU；没有路径 MTU 自适应。应采用保守报文预算或检测路径 MTU，覆盖 DATA/FEC 开销。
2. SocialBridge 只在 VoxLink 初次协商返回 null/error 时退到 relay。选中 P2P 后的传输断开直接结束加入，没有“已连接但无法传输”的中继恢复路径。恢复必须重建私密登录状态，不能复用已消费的握手。
3. ReliableUdpTransport 收到 type 8 心跳后立即 sendKeepalive，双方会互相触发回复。原正常路径对照约 13.6 秒产生 5562 个心跳帧，显著高于每侧每秒一次的定时策略。应区分请求/响应或使用定时单向保活，避免反馈循环。
4. 当前一般性 Disconnected 提示没有展示路由与传输失败原因，容易被误解成好友服务不可用。

## 下一步

- 获取房主平台、版本与失败时 latest.log，查找 TX dead / One-way dead / no ack progress。
- 用户可在双方关闭 VPN/全局隧道后重新发邀请，对照真实路径；代理环境变量 no_proxy 不会绕过系统 VPN。
- 修复报文预算、心跳反馈和建连后回退，并补充低 MTU / 延迟 / 空闲 / 模组传输回归测试。模拟复现不能替代两台真实设备的完整联机验收。

证据：research/verification/2026-10-23-network-diagnosis/。已去除账号、房间、邀请、密钥和公网地址。
