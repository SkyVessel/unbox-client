# Mouse Tweaks 开关失效排查与修复

日期：2026-10-19。本轮只修改本地源码与开发构建，未发布 GitHub Release。

## 已确认的问题

Mouse Tweaks 2.31 的 `Main.updateScreen` 在切换背包／容器界面时调用 `config.read()`。Unbox 原先每 5 个客户端 tick 写一次内存配置，随后发生的鼠标回调又把上游配置文件的默认开启值读回来；第一次操作可在下一次同步前执行。

真实游戏复现：26.3 Fabric，Unbox 关闭 Mouse Tweaks，上游文件保持默认开启。打开背包后对真实物品槽调用上游滚轮入口，仍返回“已处理”，测试失败。证据：`.cache/benchmarks/toggles-red-fabric263/logs/feature-smoke.json`。

## 修改

- 新增 `MouseTweaksGateMixin`：总开关关闭时，上游点击、拖动、释放和滚轮入口直接返回“不处理”，由原版继续接收输入。
- 关闭期间清除其已缓存的界面引用，使同一个背包界面内重新开启时也会重新初始化拖动、滚轮状态。
- 在上游界面更新／配置重读完成后，立即应用 Unbox 的四项设置，防止总开关或子选项被上游文件覆盖。
- 复用 `ClientIntegrations.refresh` 统一处理适配错误；不修改上游 JAR、默认配置文件或其他模组的开关语义。

## 另一个容易混淆的行为

Inventory Profiles Next（Inventory Sort）也有独立的 `Move items with scrolling`／滚轮移动选项。Mouse Tweaks 关闭且背包整理的滚轮功能开启时，滚轮仍会由 IPN 处理。这是另一模组的正常功能。

现有冲突控制保留：Mouse Tweaks 的滚轮功能开启时，IPN 自动停止处理同类滚轮操作；Mouse Tweaks 关闭时，IPN 恢复自己的设定。若不希望任何模组通过滚轮移动物品，还需关闭 Inventory Sort 中的滚轮选项。

## 其他模组排查范围

| 模组 | 本轮检查 |
|---|---|
| Inventory Sort / IPN | 关闭排序入口、自动补货、锁槽、Profile、滚轮选项；检查与 Mouse Tweaks 的滚轮互斥 |
| Dropped Items / ItemPhysic Lite | 关闭后上游渲染入口提前退出 |
| Bookshelf Visualizer | 关闭后停止 HUD 渲染、射线检查与请求发送入口 |
| Status Effect Bars | 关闭 HUD 与物品栏两种布局 |
| Resourcify | 关闭资源包、数据包、光影、世界和链接入口设置 |
| Spatial GUI | 检查真实 `isEnabled` 渲染开关；其 `shouldHookScreen` 只判断界面类型，不能单独当作启用状态 |
| VeinMiner / FallingTree | 检查已有服务端动作和客户端输入拦截；额外回归结果见下方 |
| Easy Shulker Boxes / Effortless Building / Happy Ghast | 检查房主权限入口和关闭逻辑；额外回归结果见下方 |
| 方块形态切换、Freecam、HUD、地图、披风、墓碑 | 代码检查入口开关；沿用此前相关运行测试，不宣称本轮逐项人工实玩 |

关闭墓碑功能后保留已有墓碑及其中物品，关闭地图后保留已探索缓存，均属于数据保留行为，不应为了“关闭”而删除玩家数据。

## 验证方法

新增 `ModToggleChecks`，在隔离世界的 `features` 模式运行真实已加载模组方法，覆盖重复打开背包、默认开启的上游配置重读、四种鼠标入口、重新开启以及逐项子设置。

`UNBOX_TOGGLE_ONLY=1` 只运行这组开关回归；不修改玩家原存档。测试结果目录：`.cache/benchmarks/mod-toggle-{fabric|neoforge}-{261|2612|263}/logs/feature-smoke.json`。

六套 Fabric／NeoForge 模块已编译成功，`verify-client-bundle.py` 校验通过。最终运行结果在下面补记。Windows 真机和同事的原操作尚未实测；不将 macOS 验证称为 Windows 实玩验证。

## 运行结果

| Minecraft | Fabric | NeoForge |
|---|---|---|
| 26.1 | 47 项通过 | 47 项通过 |
| 26.1.2 | 47 项通过 | 47 项通过 |
| 26.3 | 47 项通过 | 47 项通过 |

26.3 NeoForge 完整功能回归共 115 项通过，包含上述开关检查，以及潜影盒跟随房主开关、建筑玩法在生存中拒绝执行、乐魂关闭后恢复原速度、形态切换限制、Spatial 原生设置联动和资源浏览。证据：`.cache/benchmarks/mod-toggle-host-features-neo263/logs/feature-smoke.json`。

26.3 Fabric 生存回归共 23 组通过，包含关闭连锁挖矿／砍树后真实破坏事件保留相邻方块、重新开启后执行连锁、关闭房主功能重置挖矿模式、IPN 实际整理背包并保留物品数量及组件、ItemPhysic 的实际渲染切换。也回归了墓碑和地图原有检查。证据：`.cache/benchmarks/mod-toggle-survival-fabric263/logs/survival-smoke.json`。

结论：本轮确认并修复 Mouse Tweaks 的配置重读绕过开关；以上已检查项目未发现相同的持续关闭失效。IPN 独立滚轮功能仍按用户选项运行。未据此保证所有第三方模组、服务器或操作组合均无其他问题。
