# 功能移植与可靠性升级验收报告

基线为 `922aa363`。本轮保留包名、签名配置、AGP、Kotlin、Java 17、目标 SDK 36 和 Rhino 1.8.1；最低支持 API 已提高到 26。开源来源与许可证见 [NOTICE](../../NOTICE)，MCP 与 Web 接口见 [api.md](../../api.md)。

用户已明确将真实模型和语音 Provider 验收改为“先预留接口”。本轮未调用真实 Provider；选区 AI 问答使用本地合成响应验证应用侧请求、取消和结果归属，不代表真实模型服务验收。角色 TTS 的代码、合成测试和面板已交付，但实际语音 Provider、音质和播放验收仍暂缓。

设备测试使用本任务创建的 API 26 x86_64 和 API 36 模拟器。

## 阶段交付状态

| 阶段 | 范围 | 当前结论 |
| --- | --- | --- |
| 0 | 最低 API 与安装兼容 | API 26 模拟器覆盖升级及本地 TXT 阅读通过；89→90 迁移夹具验证旧书源、阅读进度与配置保留。已提交 `4230b03f7`。 |
| 1 | Cronet 认证隔离、阅读页所有权、WebView 借用隔离、大文本编辑与筛选排序 | 开发及 API 26/36 两设备定向回归完成；基础修复已提交 `7b1db9168`，原生大文本生命周期与所有权修复已提交 `c9eaffdd3`。安全大文本路径采用 `AppCompatEditText`，Sora 小文本编辑路径保持不变；池租约隔离保留。API 26 新 APK 22/22 通过，API 36 原生交互与所有权补验通过。 |
| 2 | 调试运行所有权、草稿试跑、预览与 AI 诊断、选区多轮问答 | 调试隔离及 API 26 会话回归通过，包括 20 KB 合成回答；已提交 `a2f7ae275`。 |
| 3 | JS 协议、89→90 迁移、Android/Web/API/备份往返、受限配置预览 | API 26 传统规则、JS 书源完整 WebBook 搜索/详情/目录/正文链路，以及 `java.md5Encode` 两项回归通过；已提交 `d86b2c742`。 |
| 4 | 六类 MCP 工具、鉴权、调试会话、前台服务与令牌备份排除 | API 26 官方 Kotlin MCP 客户端通过真实本机 HTTP 验收；API 36 系统 `dataSync` 超时已有实测证据，最终生产规则 R8 APK 的官方 Node MCP SDK 1.32.1 检查 19/19 通过，通知 Stop 通过；已提交 `6785cf547`。 |
| 5 | 手选章节分镜、角色与音色配置、系统/HTTP TTS 路由、可重建缓存 | 角色 TTS 代码、合成测试、9 项角色单测与 API 26 阅读器面板烟测通过，阶段已提交 `d55415c6c`。用户已指定真实模型/语音 Provider 先预留接口，本阶段未调用真实 Provider；实际语音、音质与跨引擎播放验收暂缓。 |

## 关键验收边界

- 阶段 3 的书源测试使用本地合成 HTTP 服务，不访问外部书源；阶段 4 通过实际 Android MCP 服务和官方客户端走本机 HTTP，不以工具注册假对象代替服务链路。
- API 26 面板截图保存在 `build/porting-validation/panels-api26/`；截图用于辅助定位，交互通过仪器断言验收。API 26 传统规则与 JS 管线记录见 `build/porting-validation/instrument-api26-final.log`，面板及拖动批次记录见 `build/porting-validation/instrument-api26-panels-drag.log`。
- API 36 实际前台服务超时验收证据：将 `data_sync_fgs_timeout_duration` 临时设为 3 秒，按 HOME 后 5.25 秒由系统超时回调结束服务；服务状态停止、监听器与系统 `ServiceRecord` 均消失，未重启，测试后恢复 `device_config`。MCP 代码未在后续编辑器提交中变化，因此该旧系统超时证据仍适用，但未在最终 APK 上重跑。最终 APK 的通知栏 Stop 已点击（action 0）；1.22 秒后端口关闭、Service 消失且未重启，见 `build/porting-validation/mcp-notification-final-api36.json`。旧超时记录为 `build/porting-validation/mcp-system-api36.json`。
- 之前生产原样 R8 APK 的 SHA-256 为 `d063c4b15edab0da739bcd4c83004d13a96c9cf62a1c826183416cf1a1543822`，官方 Node MCP SDK 对该旧 APK 的 19 项外部检查通过，见 `build/porting-validation/mcp-external-api36-before-editor-fix.log`。最终生产规则 R8 APK 对应代码提交 `c9eaffdd3`，SHA-256 为 `6b7f3b4abf2a759f87ac66e169b53fa7787591c3bff65be361c97e3b5c8d7fd4`，APK 路径为 `app/build/outputs/apk/app/release/legado_app_3.26.101919.apk`，最终产物元数据见 `build/porting-validation/final-artifact.json`。临时初始化只固定验证 `versionName` 为 `3.26.101919` 并用 debug 证书签名，没有修改生产混淆规则或正式签名配置。官方 `@modelcontextprotocol/sdk` 1.32.1 对最终 APK 的 19/19 项检查通过，见 `build/porting-validation/mcp-external-api36.log`，覆盖六工具、Host/Origin 鉴权、JSON 保存冲突与覆盖保留状态、JS 原文保存/导出、check、草稿 JS 调试与 `java.md5Encode`、草稿不落库、BUSY 释放和按自身 URL 删除清理。R8 仪器 runner 因共享 Kotlin 类被裁剪无法启动；轮次 18 仅为测试 APK 显式打包已有 Trace 依赖，生产 APK 哈希不变，runner 仍无法启动，因此完整回归使用 debug runner。
- 迁移前数据库副本保存在 `build/porting-validation/baseline89/legado.db`；它来自空数据测试安装，不是用户真实数据备份。旧记录保留由带书源、阅读进度与配置的迁移夹具验证。Gradle 轮次仅保留有界问题/结果摘要于 `build/porting-validation/`，不在报告中复制原始构建日志。
- 清理于 2026-10-20 完成：终止已核实属于本任务的旧 QEMU 实例后，原先被占用的目录已删除；另外两份模拟器 `pstore.bin` 的只读属性已清除，随后普通 `Remove-Item` 完成删除，未使用 `-Force` 或更改 ACL。已确认项目 `tmp/`、两份测试 AVD 注册和该 QEMU 进程均不存在，ADB 转发为空；构建 APK 与验收证据保留。Gradle wrapper 此前已 standalone finish 并清除其管理日志。最终状态见 [cleanup-final.json](../../build/porting-validation/cleanup-final.json)。上轮 `rejected: blocked by policy` 与旧文件占用错误属于历史记录，分别保存在 [cleanup-command.txt](../../build/porting-validation/cleanup-command.txt) 和 [cleanup.json](../../build/porting-validation/cleanup.json)，不再构成当前阻塞。本次只做清理和文档更新，未重新执行构建、设备或 Provider 验收。
- API 36 首轮仪器测试 45 项中 41 项通过、4 项失败，涉及迁移夹具未注册迁移列表、NativePanel Espresso root 焦点，以及 `CodeEditSafeEditor` 两项本地 HTML 请求 403。后续复测确认迁移、NativePanel 和 adapter 通过；这些结果不代表安全大文本通过。
- 安全大文本正式验收：API 26 新 APK 仪器回归 22/22 通过，用时 21.36 秒，日志见 `build/porting-validation/instrument-api26-native-final.log`；API 36 补跑 2/2 通过，用时 38.052 秒，见 `build/porting-validation/instrument-api36-native-final-rerun.log`。API 26 覆盖 Migration 2、adapter 1、SafeEditor 3、CodeTransfer 12、Backstage 3、NativePanel 1 项；同步输入登记和迟到输出锁保护的 4 个所有权用例也通过。SafeEditor 用例覆盖 380034 UTF-8 混合 CJK/emoji 长单行、真实键盘输入 `x`、`sendText` 替换、立即重建、`RESULT_OK` 保存与文件交接、只读及返回后继续/丢弃。API 36 首轮原生 17 项中 16 项通过；唯一失败是测试用 `typeText` 在输入前再次点击而改变预设光标，改为 `typeTextIntoFocusedView` 后补测通过。API 36 补跑覆盖真实键盘输入、立即重建与保存文件交接，以及取消 scope 后 `initData`/`Store.clear` 的输入清理；只读、返回丢弃、11 个 CodeTransfer、3 个 Backstage 也已通过。由此，原生三项交互和四个新增所有权用例在 API 26/36 均有实际通过证据。两设备的 `safe-editor.png` 已实际查看确认正常显示，分别位于 `build/porting-validation/panels-api26/safe-editor.png` 和 `build/porting-validation/panels-api36/safe-editor.png`。旧 WebView/JavaScript 大文本路径曾出现长时间约 70% 加载、renderer CPU 100% 及首次 JavaScript evaluation 超时；关闭软折行的探索还触发过 `ThreadPoolForeg` native 栈溢出（512 frames），CSS 试验已回退。该线程栈和旧路径现象未确认精确根因；最终大文本输入改走原生 `AppCompatEditText`，没有 JS 桥、HTML 脚本或临时诊断探针残留。
- 阶段 5 的 TTS 日志已避免记录原文，动态主题对比色和旁白改名保留音色绑定均已修正，角色单测通过。实际语音 Provider、音质与跨引擎播放未验收，按用户要求暂缓。
- API 26 保留仪器日志是多轮定向回归且相互重叠，不将不同轮次的测试数累加为全量通过数；最终报告按阶段和实际通过的用例描述。
- Web 编辑器在 390px 窄屏下仍有三栏布局裁切限制，本轮未重做布局。浏览器静态检查没有 App 后端，未把 Web 导入接口往返计为端到端通过。
- TTS 的真实音色、语音质量及跨引擎播放进度需要 Provider 配置后验收。合成响应、单测和面板烟测不构成这些验收的替代证据。
- 未 push、创建 PR、正式签名发布或部署。

## Gradle 验证轮次

下表逐字记录每轮 `question` 字段，并概括当轮结果；结果不复述原始 Gradle 日志。

| 轮次 | 原始问题 | 有界结果 |
| --- | --- | --- |
| 1 | 确认阶段0保持现有工具链且合并后的安装清单最低API为26 | 通过；API 26 最低版本与现有工具链约束已确认。 |
| 2 | 验证选区问答历史边界、旧请求拒绝及问题长度限制 | 未通过；遇到损坏的 Gradle 转换缓存，后续修复。 |
| 3 | 修复已确认的空转换缓存后验证依赖解析和当前阶段编译及选区问答与Cronet定向测试 | 未通过；缓存元数据仍损坏，后续隔离处理。 |
| 4 | 隔离缺失metadata的单个转换缓存后验证当前生产源码和协议依赖可编译 | 未通过；转换缓存故障尚未排除，随后重新生成缓存。 |
| 5 | 使用新构建进程重新生成已隔离的损坏转换缓存 | 缓存重新生成后仍遇到编译错误，后续轮次修复。 |
| 6 | 验证已修正的排序、JS、共享调试、MCP与朗读接口编译 | 编译问题尚未全部解决；后续轮次完成修复。 |
| 7 | 验证全部已完成移植的JVM回归并构建API26设备验收所需App与instrumentation APK | 编译问题尚未全部解决；后续轮次完成修复。 |
| 8 | 修复听书面板编译点后，定向回归、应用 APK 和仪器测试 APK 能否通过？同时确认 MCP 客户端依赖能否成功下载。 | 编译/测试仍有问题；后续修复后重跑。 |
| 9 | 补齐迁移、调试上下文和编辑器恢复后，定向单元回归及应用和仪器测试 APK 是否全部通过？ | 编译/测试仍有问题；后续修复后重跑。 |
| 10 | 固定全部入口代码并同步 Web 产物后，定向单元回归和两个调试 APK 是否通过？ | APK 通过；175 项 JVM 测试中 173 项通过，2 项测试夹具失败。 |
| 11 | 修正两份单元夹具和 MCP SDK 地址格式后，受影响单测及设备验收 APK 能否通过？ | 相关 17 项测试全部通过，设备验收 APK 通过。 |
| 12 | AI v2 密钥、问答 IO、大文本回调与新增面板修正后，相关单测及设备验收 APK 能否通过？ | 相关 6 项测试全部通过，设备验收 APK 通过。 |
| 13 | 修正书源管线和面板夹具后，仪器测试 APK 与 release 主代码能否编译？ | 仪器测试 APK 与 release 主代码编译通过。 |
| 14 | 新增拖动刷新夹具及修正端口输入滚动后，定向仪器测试 APK 能否重新构建？ | 未通过；ViewBinding 测试 getter 用法导致编译失败，随后修正。 |
| 15 | 修正 ViewBinding 的 Java getter 实现后，拖动刷新与面板回归 APK 能否编译？ | 修正后仪器测试 APK 编译通过。 |
| 16 | 现有工具链与固定 MCP/Ktor 依赖能否构建混淆 App 和 release 仪器 APK，供宿主接口及设备回归验收？ | 未通过；R8 报 Ktor 可选调试器探测引用两项 Android 缺失的 JVM 管理类型，已定位并精确处理。 |
| 17 | 对 Ktor 可选调试探测精确处理两项 JVM 缺类后，混淆 App 与 release 仪器 APK 能否构建通过？ | App 与 release 仪器 APK 完成 R8 混淆及打包；runner 后续因共享 Kotlin 类裁剪无法启动。153 项任务中执行 14 项，其余复用有效结果。 |
| 18 | 仅为测试 APK 显式打包已有 Trace 依赖后，release 仪器 APK 能否构建并保留目标 App 的生产混淆结果？ | 测试 APK 构建通过，目标生产 APK SHA-256 不变；runner 启动仍因 Kotlin 共享类裁剪失败。 |
| 19 | 拖动刷新夹具改用正式 bindViewHolder 后，API36 完整回归所需的 debug 仪器 APK 能否构建？ | 通过；debug 仪器 APK 构建耗时 144.859 秒，5 项任务执行、88 项任务复用。API 36 首轮仪器结果见阶段 5 与关键验收边界。 |
| 20 | 修复安全编辑器主文档拦截、旁白身份和主题后，角色单测、debug 验收 APK 与生产 R8 APK 能否通过？ | 通过；689.125 秒，9 项角色单测及 debug、debug 仪器、生产 R8 APK 构建通过。API36 大文本验收仍未通过。 |
| 21 | 移除 WebView 池的进程级计时器控制后，编辑器与复用隔离回归所需 debug APK 能否构建？ | 通过；227.781 秒，确认该临时诊断改动可构建。该全局计时器改动已恢复；最终保留 WebView 池租约隔离修复。 |
| 22 | 仅加入不含正文的 DEBUG WebView 事件诊断后，诊断 App APK 能否构建以定位 API36 白屏？ | 通过；274.156 秒。临时诊断事件不包含正文。 |
| 23 | 将本地 HTML 加载改为官方要求的 HTTP(S) 基址后，API36 对照验收 APK 能否构建？ | 通过；237.89 秒，仅确认对照验收 APK 构建成功。该基址调整未单独解决大文本操作的 JavaScript evaluation 超时。 |
| 24 | 仅记录安全编辑器脚本初始化阶段后，API36 定位 APK 能否构建？ | 通过；270.188 秒，仅确认定位 APK 构建成功，运行验收仍未通过。 |
| 25 | 安全编辑器关闭超长单行软折行后，同一 API36 设备对照 APK 能否构建？ | 通过；127.641 秒，仅确认对照 APK 构建成功。运行时出现 `ThreadPoolForeg` 线程 native 栈溢出（512 frames），CSS 试验已回退；此现象不能据此归因到 V8 或布局。 |
| 26 | 将大文本载荷移出可执行 JS 源码后，API36 同一用例的对照 APK 能否构建？ | 通过；102.547 秒，仅确认对照 APK 构建成功。运行时首次 JavaScript evaluation 仍超时，大文本验收未通过。 |
| 27 | 复用生产编辑器的 ASCII/中文单行及多行诊断夹具能否构建？ | 通过；84.063 秒，仅确认诊断 APK 构建成功。 |
| 28 | 较大文本与 emoji 对照及可丢弃的原生编辑器原型夹具能否构建？ | 未通过；52.563 秒，原型测试访问 `protected viewModel` 导致编译失败，随后修正夹具。 |
| 29 | 原型夹具改用真实显示文本判断就绪后，文本形状对照 APK 能否构建？ | 通过；61.281 秒，仅确认对照 APK 构建成功。形状测试与原型运行结果见关键验收边界；生产大文本路径及保存重建验收仍待完成。 |
| 30 | 原生大文本安全路径、同步重建和迟到输出所有权修复后，App 与完整定向仪器 APK 能否构建？ | 通过；242.547 秒，App 与完整定向仪器 APK 构建成功。当轮 API 26/36 运行验收尚待进行；输入 token 在 IO 前取消的清理和真实键盘光标夹具后续见轮次 31。 |
| 31 | 输入 token 在 IO 前取消的清理修复及真实键盘光标夹具修正后，定向验收 APK 能否构建？ | 通过；168.953 秒，定向验收 APK 构建成功。后续 API 26 新 APK 回归 22/22 通过，API 36 原生交互及所有权补验通过，详情见关键验收边界。 |
| 32 | API26/36 大文本与所有权回归通过后，最终生产代码的 R8 验证 APK 能否构建？ | 通过；564.562 秒，123 项任务中 23 项执行、100 项复用，无警告。最终产物与 SHA-256 见关键验收边界；官方 Node MCP SDK 1.32.1 对最终 APK 19/19 通过，通知 Stop 通过。逐轮摘要见 `build/porting-validation/gradle-summary.json`。 |
