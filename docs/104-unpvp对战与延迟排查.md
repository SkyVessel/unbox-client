# unpvp 对战与延迟排查

日期：2026-11-14。分支：`codex/unpvp-ui`。本轮修改源码并重新构建核心 / PvP JAR；未发布 Release，未更新 Windows 安装包，未重打 macOS 启动器。现有已安装启动器不会自动获得这些修复。

## 用户反馈

Windows，同一台电脑与网络，Unbox / Lunar 可对应设置相同。Hypixel 大厅 FPS 相差两倍多；正式对局中攻击同一对手，Unbox 的击退表现较差。HUD 大厅约 189 ms，入局立即变为 1 ms，返回大厅约 210 ms。Toggle Sprint 开启，Shaders 状态未知。是否每次攻击实际扣血尚未确认；不能把“后者打开，前者不知道”解释成已确认不扣血。

本轮已定位两个具体功能错误和一个测量来源问题，**尚未证明没有击退或大厅帧率差距已经解决**。性能调查继续以 [103](103-unpvp-Windows-Hypixel大厅性能调查.md) 为准。

## 1. Hit Color：OptiFine 光影旁路

原实现只修改 `RendererLivingEntity.setBrightness` 写入 `FloatBuffer` 的四个分量。OptiFine M5 光影分支另调用 `Shaders.setEntityColor(1,0,0,.3)`，绕开修改后的缓冲，因此仍使用红色。

在 `DamageTintMixin` 增加可选参数挂钩，将同一颜色、透明度、渐隐设置用于该光影调用。不额外启用光影，不改变正常渲染路径。测试仅验证引擎传给光影的颜色；第三方光影包可能自行忽略该值。

普通渲染之前已经能够变色：隔离世界内实际渲染玩家模型，红/绿设置对比有 8,320 个像素变化。光影路径修复前实际 uniform 为 `[1,0,0,.3]`，修复后为配置的 `[0,1,0,.7]`。这并不确认用户当时使用了光影，也不确认其 Hit Color 现象全部来自这个分支。

## 2. Toggle Sprint：非切换状态覆盖原版疾跑

原 `PvpControls.tick()` 在模块开启但切换意图为 false 时，每 tick 调用 `setSprinting(false)`，会取消原版双击前进等方式的疾跑。

修复为仅在按键真正把切换意图从开变关时停止一次；其余时间交由原版处理。保留切换意图、菜单开关后恢复、饥饿/潜行/使用物品等原有判断。未加入额外 START/STOP 数据包或所谓击退增强。

这个错误可能影响疾跑状态；没有证据表明它能解释用户所有对局中的击退差异。模块开启和“当前已切换为疾跑”是两个不同状态。

## 3. Ping HUD：从 TAB 数值改为连接响应估计

原 `LiveHud` 将玩家列表中的 `NetworkPlayerInfo.getResponseTime()` 直接显示为延迟。客户端收到 1 就显示 1，无法代表实测玩家到服务器的响应时间。没有查证 Hypixel 当前各游戏后端设置该字段的具体规则。

新增 `Latency` / `LatencyMixin`：

- 在当前游戏连接上发送标准 `C16 REQUEST_STATS`，观察 `S37 Statistics` 返回；最多每 10 秒一次，单个待回应请求，不另建网络连接，不阻塞游戏线程。
- 发送时记单调时钟，接收时在网络线程记时；包含出站等待及服务端处理，排除了返回后游戏主线程排队的等待。因此显示为 `~189 ms` 一类估计值，不宣称纯网络 RTT、TCP ping 或输入到画面的总延迟。
- 断线、换世界、Join/Respawn 清除旧样本；无样本显示 `-- ms`，单人世界显示 `Local`。关闭 Ping 不继续探测。
- 其他统计请求会使待测样本失效，打开原版 Statistics 时暂缓；样本 30 秒后过期。
- 5 秒超时后停止探测直到重置，防止迟到回复被归给下个请求；不回应此请求的服务器会显示 `--`，不退回伪装为实测值的 TAB 数字。
- 协议回复没有请求 ID，主动推送的统计更新仍可能与待测响应混淆。因此它是近似测量。没有承诺所有服务端均支持或每个样本完全准确。
- 观察挂钩不取消、不替换、不延迟攻击、移动、KeepAlive 或击退包。

公开参考：[Skytils 官方 Ping 源码](https://github.com/Skytils/SkytilsMod/blob/1.x/src/main/kotlin/gg/skytils/skytilsmod/features/impl/misc/Ping.kt) 使用此标准请求/回复思路。本轮状态管理和挂钩独立编写，未复制其 AGPL 实现。原 Player List 数字延迟仍是服务器字段；本轮只修正独立 Ping HUD，不将 TAB 数字冒充实测值。

## 4. 对战路径检查与限制

普通 Hypixel 连接沿用原版服务器连接；VoxLink / e4mc 用于好友邀请世界。没有发现本次 Unbox 控制、动画或 Patcher 代码取消攻击请求、拦截速度包或修改击退倍率。

隔离测试已确认：实际 PlayerController 攻击调用能抵达出站通道；本地及远端实体收到速度包后 XYZ 分量保持原值；受伤状态包能设置远端 hurtTime。这里主动给本地处理器输入测试包，**不是 Hypixel 的真实伤害、服务端反作弊、TCP 延迟或对手击退测试**。

仍需 Windows 正式对局验证：是否实际扣血、双方疾跑状态、测量后的响应值及同地点帧时间；不要通过修改攻击距离、伪造包或强制服务端击退来掩盖问题。

## 5. 构建与验证证据

构建顺序：`python3 scripts/build-unpvp.py`，随后 `python3 scripts/build-unpvp-pvp.py`。

测试命令：

```sh
python3 scripts/test-unpvp.py combat-fixed-01 --combat --mods "$PWD/.cache/unpvp-performance/jars"
```

`--combat` 自动启用原 PvP 回归；使用独立测试身份/存档，不加入公共服务器。探针只有显式测试参数才运行。

| 测试目录（`.cache/unpvp-tests/`） | 结果 |
| --- | --- |
| `combat-baseline-01` | 原普通路径模型像素、速度与受伤状态检查通过 |
| `combat-shader-before` | 光影颜色断言失败，证实红色常量绕过；退出阶段另有原生崩溃 -6，不能算成功运行 |
| `combat-sprint-before` | 光影修复已通过；原版疾跑保留断言失败，证实每 tick 取消问题 |
| `combat-fixed-01` | 15 项 combat 检查与 97 项既有 PvP 回归通过，进程退出码 0 |

15 项含普通玩家像素、光影 uniform、攻击出站、双向速度、受伤状态、原版疾跑保留、测量限频/过期/歧义/超时、真实本地统计请求响应、无 GL 错误。测试使用本机 Apple Silicon、Forge 1.8.9 及完整已固定性能包。没有新增 Windows FPS 或公网稳定性成绩。

日志和构建输出：`.cache/unpvp-combat/`；运行日志：`.cache/unpvp-tests/combat-fixed-01/launch.log`。生成资源：`src-tauri/resources/unpvp-forge-1.8.9.jar`、`src-tauri/resources/unpvp-pvp.jar`。下一次打包必须一起包含两个 JAR，不能只更新核心。
