# TXT 目录规则滚动修复

页面规格见 [SCREEN_SPEC_TXT_RULE.md](SCREEN_SPEC_TXT_RULE.md)。漫画分类与实际窗口截图统一记录于 [VISUAL_REVIEW.md](../../VISUAL_REVIEW.md)。

## 确认路径

- `TxtTocRuleDao.observeAll()` 按 `serialNumber` 返回全量规则；管理页与阅读器 picker 共用 `TxtTocRuleList` 的 `LazyColumn`。本路径没有分页请求或数量上限。
- 原长按处理消费移动事件，并只从 `visibleItemsInfo` 选择目标；到达视口边缘后缺少滚动驱动，排序和连选停在当前可见范围。
- 管理页设备回归已观察到长按连选仅命中首行，排序事件没有移动规则。实际 Foundation 1.10.6 的 `detectDragGesturesAfterLongPress` 在 `Main` pass 等待移动，内层 `LazyColumn` scrollable 先消费后会转入取消分支；这与设备症状一致，取消分支本身未单独记录计数。
- 原 `pointerInput(rules.size, selectionMode)` 捕获旧回调，同数量的重组保留旧处理器。快速连选直接跳到目标行，漏掉中间行。
- `TxtTocRule.equals()` 只比较 `id`。原编辑草稿使用结构相等状态，修改字段生成的同 id `copy` 被状态策略忽略；列表单项替换也受同一语义影响。

## 改动

- 共用列表在长按时按指针距边缘的比例滚动；使用帧时间驱动，并在结束或取消时停止。管理排序、管理连选与 picker 排序使用同一条路径。
- 复用 Foundation 的按下及长按识别，长按成功后在 `Initial` pass 消费移动与抬手，先于内层 scrollable；普通快速滑动继续由现有列表处理。取消与结束通过同一个 `finally` 停止滚动并保存实际排序变化。
- 拖动身份使用规则 `id`，回调和 id 集合通过 `rememberUpdatedState` 更新；快速连选补齐经过的行，实际排序变化后再保存。
- 手势在 `onMove` 后同步本次 id 顺序，通过拖动 id 查找当前源索引、通过可见位置索引命中指针目标；同帧重复移动与跨多个目标均保留最终顺序。重排时保留可见索引与偏移，避免首项 key 自动锚定到新位置。
- 管理页、picker 使用引用相等的列表状态，一次替换完整快照；编辑草稿使用引用相等策略，保留实体既有 id 相等契约。
- picker 数据收集归属 `viewLifecycleOwner`，视图重建时释放原收集器。
- 保留现有编辑、勾选、启停、置顶/置底、删除和导入导出入口；数据库结构及 DAO 保持现状。

## 回归用例与验证

`TxtTocRuleContentSemanticsTest`：既有控件语义，以及 120 条规则的管理/picker 快速滑动与末项可达、长按边缘越过首屏、同数量重组后的新回调、快速连选的中间项覆盖、同帧重复目标/不同目标的完整序列与首项重排视口保持。

`TxtTocRuleActivityInstrumentedTest`：插入独立 UUID fixture，操作实际编辑对话框修改名称和正则、保存、启停并重建 Activity；完成后仅删除该 fixture。第一条编辑字段回读断言覆盖同 id 草稿更新。

API36 常规窗口的 16 项定向 instrumentation 全部通过，46.901s：漫画分组 3 项、规则内容 10 项、编辑器 1 项、真实 Activity 编辑保存与重建 1 项、书架 Tab 交互 1 项。此前 6 个拖动用例均曾失败；修正长按后的事件接管后复验全部通过。

320dp 实际窗口的规则内容 10 项再次全部通过，34.395s。实际 App 另外以原生 `draganddrop` 验证长按连选跨过首屏，显示 8/26；返回重进为 0/26，随后普通滑动完整到达末项“默认分章规则”。截图与尺寸见视觉验收报告。

本次未增加分页、错误恢复层或新测试框架；真实导入/导出 Provider、夜间主题及设备帧率仍按主线程实际执行记录判断。

## 构建记录

所有 Gradle 命令经 `gradle-run` 工作流 `bf00e62ca73a0b653a5fcf6e0a09466c`，限定离线任务及两个 worker。

| 验证问题（原文） | 实际结果 |
| --- | --- |
| “Can manga groups and TXT rule scrolling fixes compile with their instrumentation regressions?” | 首次运行 700.484s，应用编译通过；测试在文本替换操作后链式调用断言，返回类型为 Unit，测试编译失败。已定位对应两行并拆开调用。 |
| “Do the final manga and TXT rule fixes compile after correcting text assertions?” | 应用与测试 APK 通过，128.125s；仅保留既有测试警告。 |
| “Are Gradle daemons stopped before device validation?” | 成功停止一个 daemon，2.015s。 |
| “Does the complete long-press gesture regression compile without an intermediate idle wait?” | 测试包通过，116.766s。 |
| “Do the corrected drag coordinates and clock-driven edge gesture tests compile?” | 测试包通过，91.031s。 |
| “Do long-press event ownership and the manga shelf compile with their regression tests?” | 应用与测试 APK 通过，232.750s，无 warning/failure fingerprint。设备复验前再次成功停止 daemon。 |

首批设备运行中，漫画数据库 3 项通过；随后边缘长按用例停滞，主线程主动停止该次 instrumentation。测试改为一次注入完整的长按、边缘停留与抬手。坐标也明确落在内容排序区域，连选仍使用 Checkbox；之后完整运行 16 项，10 过、6 个拖动失败（45.056s），提供了长按事件竞争的设备证据。最终生产修复后 16/16 通过。每个用例配置 `timeout_msec=30000`，测试 fixture 在 finally 清理。

工作流已 finish，清理范围仅为包装器拥有的日志。取消及多指接续仅核对源码；本轮未独立注入这两类事件，未测试真机性能、TalkBack、导入/导出 Provider 或夜间主题。
